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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * 인기글 목록 캐시 이름(CacheConfig에서 TTL 5분). 쓰기(create/update/delete) 시 전체 무효화한다.
     * 단일 키 리스트 캐시라 선택 무효화가 불가능해 {@code allEntries=true}로 비운다.
     * {@code beforeInvocation=true}: 기본(afterInvocation)은 트랜잭션 인지 캐시가 커밋 이후로 무효화를 미뤄
     * 결정적이지 않다. 쓰기 전에 즉시 무효화하면 확실하다 — 짧은 staleness 창은 5분 TTL로 수렴하므로 허용.
     */
    private static final String POPULAR_POSTS_CACHE = "popularPosts";
    private static final int POPULAR_POSTS_LIMIT = 10;

    @CacheEvict(value = POPULAR_POSTS_CACHE, allEntries = true, beforeInvocation = true)
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
        // 조회수는 DB에서 원자적으로 증가시킨다(동시 요청 lost update 방지, Phase 16).
        // UPDATE 먼저 → 영향 행 0이면 없거나 soft-deleted → 404. 그 후 fresh read로 증가된 값을 읽는다.
        int updated = postRepository.incrementViewCount(id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post", id);
        }
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        long likeCount = postLikeRepository.countByPostId(id);
        boolean liked = memberId != null && postLikeRepository.existsByMemberIdAndPostId(memberId, id);
        return PostResponse.of(post, likeCount, liked);
    }

    public Page<PostListResponse> findAll(PostSearchCondition condition, Pageable pageable) {
        log.debug("게시글 목록 조회: keyword={}, author={}, page={}",
                condition.keyword(), condition.author(), pageable.getPageNumber());
        return postRepository.search(condition, pageable);
    }

    /**
     * 조회수 상위 인기글 목록(Phase 16: Redis 캐싱). 모든 사용자에게 동일하고 읽기 부하가 커 캐시에 적합하다.
     * 단건 조회({@code findById})는 호출마다 조회수를 증가시키고 사용자별 {@code liked}가 달라 캐싱하지 않는다.
     */
    @Cacheable(POPULAR_POSTS_CACHE)
    public List<PostListResponse> findPopular() {
        log.debug("인기글 목록 조회(캐시 미스 → DB)");
        return postRepository.findPopular(POPULAR_POSTS_LIMIT);
    }

    @CacheEvict(value = POPULAR_POSTS_CACHE, allEntries = true, beforeInvocation = true)
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

    @CacheEvict(value = POPULAR_POSTS_CACHE, allEntries = true, beforeInvocation = true)
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
