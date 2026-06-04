package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;

import java.util.List;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public CommentResponse create(Long postId, Long memberId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Comment comment = Comment.builder()
                .content(request.content())
                .member(member)
                .build();
        post.addComment(comment);
        Comment saved = commentRepository.save(comment);
        log.info("댓글 생성 완료: id={}, postId={}, author={}", saved.getId(), postId, member.getUsername());
        return CommentResponse.from(saved);
    }

    public List<CommentResponse> findByPostId(Long postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        return commentRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(CommentResponse::from)
                .toList();
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
        commentRepository.delete(comment);
        log.info("댓글 삭제 완료: id={}", id);
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
