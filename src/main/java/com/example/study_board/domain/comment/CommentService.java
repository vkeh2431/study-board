package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentResponse create(Long postId, Long memberId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Comment parent = resolveParent(request.parentId(), postId);
        Comment comment = Comment.builder()
                .content(request.content())
                .member(member)
                .parent(parent)
                .build();
        post.addComment(comment);
        Comment saved = commentRepository.save(comment);
        log.info("댓글 생성 완료: id={}, postId={}, parentId={}, author={}",
                saved.getId(), postId, request.parentId(), member.getUsername());

        // 알림 도메인 이벤트 발행(Phase 18). 알림 생성을 직접 호출하지 않아 결합이 끊긴다.
        // 수신자 산출에 필요한 식별자는 트랜잭션 안(LAZY 프록시 접근 가능)에서 FK id로만 뽑아 원시값으로 싣는다.
        Long postOwnerId = post.getMember().getId();
        Long parentOwnerId = (parent != null) ? parent.getMember().getId() : null;
        eventPublisher.publishEvent(new CommentCreatedEvent(
                saved.getId(), postId, post.getTitle(), postOwnerId,
                request.parentId(), parentOwnerId, memberId, member.getUsername()));

        return CommentResponse.from(saved);
    }

    /**
     * 대댓글의 부모를 조회·검증한다(Phase 17). parentId가 null이면 루트 댓글이라 null을 반환한다.
     * 부모가 없으면 404, 부모가 다른 게시글의 댓글이면 400(BAD_REQUEST). 무제한 depth라 depth 제한은 두지 않는다.
     */
    private Comment resolveParent(Long parentId, Long postId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", parentId));
        if (!parent.getPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return parent;
    }

    public List<CommentResponse> findByPostId(Long postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        List<Comment> flat = commentRepository.findByPostIdOrderByCreatedAtDesc(postId);
        return assembleTree(flat);
    }

    /**
     * 평면 댓글 리스트(한 게시글 전체, member fetch 완료)를 메모리에서 재귀 트리로 조립한다(Phase 17).
     * 1쿼리로 모두 읽어 O(n) Map 조립이라 계층 댓글 N+1이 없다(Phase 8 연계). {@code children} LAZY 로딩을 쓰지 않는다.
     *
     * <p>루트는 DB 정렬(createdAt DESC)을 유지하고, 대댓글은 스레드 가독성을 위해 작성순(ASC)으로 정렬한다.
     * 부모가 목록에 없는 고아 댓글(부모가 하드 soft delete돼 @SQLRestriction에 걸린 경우)은 루트로 승격해 누락을 막는다.
     */
    private List<CommentResponse> assembleTree(List<Comment> flat) {
        Map<Long, CommentResponse> byId = new HashMap<>();
        for (Comment comment : flat) {
            byId.put(comment.getId(), CommentResponse.treeNode(comment));
        }
        List<CommentResponse> roots = new ArrayList<>();
        for (Comment comment : flat) {
            CommentResponse node = byId.get(comment.getId());
            Long parentId = comment.getParentId();
            CommentResponse parentNode = parentId == null ? null : byId.get(parentId);
            if (parentNode == null) {
                roots.add(node);
            } else {
                parentNode.replies().add(node);
            }
        }
        for (CommentResponse node : byId.values()) {
            node.replies().sort(Comparator.comparing(CommentResponse::createdAt)
                    .thenComparing(CommentResponse::id));
        }
        return roots;
    }

    @Transactional
    public CommentResponse update(Long id, Long memberId, Role role, CommentUpdateRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        verifyOwnership(comment, memberId, role);
        comment.update(request.content());
        log.info("댓글 수정 완료: id={}", id);
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long id, Long memberId, Role role) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        verifyOwnership(comment, memberId, role);
        // 대댓글 삭제 정책(Phase 17): 자식이 있으면 트리 유지를 위해 tombstone(본문만 가림), 없으면 하드 soft delete(트리에서 제거).
        if (commentRepository.countByParentId(id) > 0) {
            comment.markDeleted();
            log.info("댓글 tombstone 처리(자식 있음): id={}", id);
        } else {
            commentRepository.delete(comment);
            log.info("댓글 삭제 완료: id={}", id);
        }
    }

    /**
     * 작성자 본인 또는 ADMIN만 통과시킨다(소유권 인가). 그 외에는 403을 던진다.
     */
    private void verifyOwnership(Comment comment, Long memberId, Role role) {
        if (role != Role.ADMIN && !comment.isOwner(memberId)) {
            throw new ForbiddenException();
        }
    }
}
