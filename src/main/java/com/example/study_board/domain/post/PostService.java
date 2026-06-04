package com.example.study_board.domain.post;

import com.example.study_board.domain.category.Category;
import com.example.study_board.domain.category.CategoryRepository;
import com.example.study_board.domain.like.PostLikeRepository;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.tag.Tag;
import com.example.study_board.domain.tag.TagRepository;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public PostResponse create(Long memberId, PostCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .member(member)
                .build();
        applyCategory(post, request.categoryId());
        applyTags(post, request.tagNames());
        Post saved = postRepository.save(post); // cascade=PERSIST로 PostTag도 함께 저장
        log.info("게시글 생성 완료: id={}, author={}", saved.getId(), member.getUsername());
        return PostResponse.of(saved, 0L, false); // 갓 생성된 글: 좋아요 0, 미좋아요
    }

    @Transactional
    public PostResponse findById(Long id, Long memberId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        post.incrementViewCount();
        long likeCount = postLikeRepository.countByPostId(id);
        boolean liked = memberId != null && postLikeRepository.existsByMemberIdAndPostId(memberId, id);
        return PostResponse.of(post, likeCount, liked);
    }

    public Page<PostListResponse> findAll(PostSearchCondition condition, Pageable pageable) {
        log.debug("게시글 목록 조회: keyword={}, author={}, page={}",
                condition.keyword(), condition.author(), pageable.getPageNumber());
        return postRepository.search(condition, pageable);
    }

    @Transactional
    public PostResponse update(Long id, Long memberId, Role role, PostUpdateRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        verifyOwnership(post, memberId, role);
        post.update(request.title(), request.content());
        applyCategory(post, request.categoryId());
        applyTags(post, request.tagNames());
        log.info("게시글 수정 완료: id={}", id);
        long likeCount = postLikeRepository.countByPostId(id);
        boolean liked = postLikeRepository.existsByMemberIdAndPostId(memberId, id);
        return PostResponse.of(post, likeCount, liked);
    }

    @Transactional
    public void delete(Long id, Long memberId, Role role) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        verifyOwnership(post, memberId, role);
        postRepository.delete(post);
        log.info("게시글 삭제 완료: id={}", id);
    }

    /**
     * 작성자 본인 또는 ADMIN만 통과시킨다(소유권 인가). 그 외에는 403을 던진다.
     * 리소스 존재 확인({@code findById}) 이후에 호출하므로 없는 리소스는 항상 404가 우선한다.
     */
    private void verifyOwnership(Post post, Long memberId, Role role) {
        if (role != Role.ADMIN && !post.isOwner(memberId)) {
            throw new ForbiddenException();
        }
    }

    /** categoryId가 null이면 카테고리 해제(PUT 전체 교체 의미), 값이 있으면 해당 카테고리로 설정한다. */
    private void applyCategory(Post post, Long categoryId) {
        Category category = (categoryId == null) ? null
                : categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        post.assignCategory(category);
    }

    /**
     * 게시글의 태그를 요청 목록으로 맞춘다(생성·수정 공용). 없는 태그명은 새로 만들고, 같은 이름 태그는 재사용한다.
     * diff 방식(없앨 것 제거 → 새 것만 추가)이라 같은 태그를 지웠다 다시 넣는 일이 없어
     * orphanRemoval의 "insert 후 delete" flush 순서로 인한 유니크 제약 충돌을 피한다.
     */
    private void applyTags(Post post, List<String> tagNames) {
        Set<String> desired = (tagNames == null) ? Set.of()
                : tagNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toSet());

        post.getPostTags().removeIf(postTag -> !desired.contains(postTag.getTag().getName()));

        Set<String> existing = post.getPostTags().stream()
                .map(postTag -> postTag.getTag().getName())
                .collect(Collectors.toSet());
        for (String name : desired) {
            if (!existing.contains(name)) {
                Tag tag = tagRepository.findByName(name)
                        // 동시 생성 시 uk_tag 위반 가능 — 학습 범위에선 단순 get-or-create로 둔다
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
                post.addTag(tag);
            }
        }
    }
}
