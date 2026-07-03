package com.example.study_board.domain.post;

import com.example.study_board.domain.category.Category;
import com.example.study_board.domain.category.CategoryRepository;
import com.example.study_board.domain.comment.Comment;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.tag.Tag;
import com.example.study_board.domain.tag.TagRepository;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.global.config.JpaAuditingConfig;
import com.example.study_board.global.config.QueryDslConfig;
import com.example.study_board.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntityManager entityManager;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("author@example.com")
                .username("작성자")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long memberId) {
        CustomUserDetails principal =
                new CustomUserDetails(memberId, "auditor@example.com", "감사자", null, Role.USER);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private Post createPost(String title, String content) {
        return Post.builder()
                .title(title)
                .content(content)
                .member(member)
                .build();
    }

    /** created_at을 결정적으로 제어해 정렬 테스트의 타임스탬프 동률(flaky)을 막는다. */
    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE post SET created_at = :t WHERE id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private Post savePostWith(String title, Category category, String... tagNames) {
        Post post = Post.builder().title(title).content("내용").member(member).build();
        post.assignCategory(category);
        for (String name : tagNames) {
            Tag tag = tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
            post.addTag(tag);
        }
        return postRepository.save(post); // cascade=PERSIST로 PostTag도 함께 저장
    }

    @Test
    @DisplayName("게시글 저장")
    void save_post() {
        Post post = createPost("제목", "내용");

        Post saved = postRepository.save(post);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("내용");
        assertThat(saved.getMember().getUsername()).isEqualTo("작성자");
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 createdBy는 NULL")
    void save_post_without_authentication_leaves_createdBy_null() {
        Post saved = postRepository.saveAndFlush(createPost("제목", "내용"));

        assertThat(saved.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("인증된 사용자가 작성하면 createdBy에 memberId가 주입된다")
    void save_post_populates_createdBy_from_authentication() {
        authenticateAs(99L);

        Post saved = postRepository.saveAndFlush(createPost("제목", "내용"));

        assertThat(saved.getCreatedBy()).isEqualTo(99L);
        assertThat(saved.getLastModifiedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("게시글 단건 조회")
    void findById_post() {
        Post saved = postRepository.save(createPost("제목", "내용"));

        Optional<Post> found = postRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("제목");
        assertThat(found.get().getContent()).isEqualTo("내용");
        assertThat(found.get().getMember().getUsername()).isEqualTo("작성자");
    }

    @Test
    @DisplayName("게시글 목록 조회")
    void findAll_posts() {
        postRepository.save(createPost("제목1", "내용1"));
        postRepository.save(createPost("제목2", "내용2"));

        List<Post> posts = postRepository.findAll();

        assertThat(posts).hasSize(2);
    }

    @Test
    @DisplayName("게시글 삭제 - soft delete: 조회에서는 제외되지만 행은 보존되고 deleted_at이 설정된다")
    void delete_post() {
        Post saved = postRepository.saveAndFlush(createPost("제목", "내용"));
        Long id = saved.getId();

        postRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        // @SQLRestriction으로 조회에서 제외
        assertThat(postRepository.findById(id)).isEmpty();
        // 행은 물리적으로 보존 (네이티브 쿼리로 @SQLRestriction 우회)
        Long count = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM post WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        assertThat(count).isEqualTo(1L);
        // deleted_at이 설정됨
        Object deletedAt = entityManager
                .createNativeQuery("SELECT deleted_at FROM post WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(deletedAt).isNotNull();
    }

    @Test
    @DisplayName("제목으로 키워드 검색")
    void search_by_keyword_in_title() {
        postRepository.save(createPost("Spring Boot 입문", "내용1"));
        postRepository.save(createPost("JPA 학습", "내용2"));
        postRepository.save(createPost("Spring Security", "내용3"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition("Spring", null, null, null), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(post -> post.title().contains("Spring"));
    }

    @Test
    @DisplayName("내용으로 키워드 검색")
    void search_by_keyword_in_content() {
        postRepository.save(createPost("제목1", "Spring Boot는 쉽다"));
        postRepository.save(createPost("제목2", "JPA는 어렵다"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition("Spring", null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("제목1");
    }

    @Test
    @DisplayName("작성자명으로 검색")
    void search_by_author() {
        Member other = memberRepository.save(Member.builder()
                .email("other@example.com").username("다른작성자").password("encoded").role(Role.USER).build());
        postRepository.save(createPost("제목1", "내용1")); // 작성자: member("작성자")
        postRepository.save(Post.builder().title("제목2").content("내용2").member(other).build());

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition(null, "다른", null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).authorName()).isEqualTo("다른작성자");
    }

    @Test
    @DisplayName("키워드 + 작성자 조건을 함께(AND) 적용 - 한쪽만 만족하는 글은 모두 제외된다")
    void search_by_keyword_and_author() {
        Member other = memberRepository.save(Member.builder()
                .email("other@example.com").username("다른작성자").password("encoded").role(Role.USER).build());
        // author="다른"은 "다른작성자"만 매칭하고 "작성자"는 제외한다 → keyword/author 두 술어가 각각 한 건씩 떨궈야 정답 1건.
        postRepository.save(createPost("Spring 입문", "내용1"));                                          // 작성자, Spring O  → author로 배제
        postRepository.save(Post.builder().title("Spring 심화").content("내용2").member(other).build());  // 다른작성자, Spring O → 정답
        postRepository.save(Post.builder().title("JPA 기초").content("내용3").member(other).build());     // 다른작성자, Spring X → keyword로 배제

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition("Spring", "다른", null, null), pageable);

        assertThat(result.getContent())
                .extracting(PostListResponse::title)
                .containsExactly("Spring 심화"); // keyword 술어 빠지면 JPA 기초 포함(2건), author 술어 빠지면 Spring 입문 포함(2건) → 둘 다 깨짐
        assertThat(result.getContent().get(0).authorName()).isEqualTo("다른작성자");
    }

    @Test
    @DisplayName("조건이 없으면 전체 조회")
    void search_without_condition_returns_all() {
        postRepository.save(createPost("제목1", "내용1"));
        postRepository.save(createPost("제목2", "내용2"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("검색 결과에 댓글 수가 집계된다 (COUNT 서브쿼리 projection)")
    void search_aggregates_comment_count() {
        Post post = postRepository.save(createPost("제목", "내용"));
        entityManager.persist(Comment.builder().content("댓글1").member(member).post(post).build());
        entityManager.persist(Comment.builder().content("댓글2").member(member).post(post).build());
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).commentCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("카테고리로 검색하면 해당 카테고리 글만 나오고 결과에 카테고리명이 담긴다")
    void search_by_category() {
        Category spring = categoryRepository.save(Category.builder().name("스프링").build());
        Category jpa = categoryRepository.save(Category.builder().name("JPA").build());
        savePostWith("스프링 글", spring);
        savePostWith("JPA 글", jpa);

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, spring.getId(), null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("스프링 글");
        assertThat(result.getContent().get(0).categoryName()).isEqualTo("스프링");
    }

    @Test
    @DisplayName("카테고리 없는 글도 전체 조회에 포함된다 (LEFT JOIN 검증)")
    void search_includes_post_without_category() {
        Category spring = categoryRepository.save(Category.builder().name("스프링").build());
        savePostWith("카테고리 있음", spring);
        postRepository.save(createPost("카테고리 없음", "내용")); // category == null

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(PostListResponse::categoryName)
                .containsExactlyInAnyOrder("스프링", null);
    }

    @Test
    @DisplayName("태그명으로 검색")
    void search_by_tag() {
        savePostWith("자바 글", null, "java", "spring");
        savePostWith("파이썬 글", null, "python");

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, "spring"), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("자바 글");
    }

    @Test
    @DisplayName("여러 태그를 가진 글은 태그 검색 시 1건으로만 집계되고, 그 태그가 없는 글은 제외된다 (EXISTS 상관 서브쿼리)")
    void search_by_tag_no_duplicate() {
        savePostWith("멀티태그 글", null, "java", "spring", "jpa"); // 검색 태그 java 보유
        savePostWith("무관한 글", null, "python");                  // java 미보유 → 제외돼야 함

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, "java"), pageable);

        // 한 글이 태그 3개를 가져도 결과는 1건(중복 없음). 상관 조건(postTag.post == post)을 빠뜨리면
        // '무관한 글'까지 EXISTS를 통과해 2건이 되어 깨진다 → 서브쿼리 상관성에 teeth.
        assertThat(result.getContent())
                .extracting(PostListResponse::title)
                .containsExactly("멀티태그 글");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드 검색 결과 없음")
    void search_returns_empty_when_no_match() {
        postRepository.save(createPost("제목", "내용"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition("없는키워드", null, null, null), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("페이징 동작 확인")
    void findAll_with_pageable() {
        for (int i = 1; i <= 15; i++) {
            postRepository.save(createPost("제목" + i, "내용" + i));
        }

        PageRequest pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> firstPage = postRepository.findAll(pageable);

        assertThat(firstPage.getContent()).hasSize(5);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
    }

    @Test
    @DisplayName("허용된 정렬 키(viewCount)로 동적 정렬된다")
    void search_sorts_by_allowed_view_count() {
        Post low = postRepository.saveAndFlush(createPost("조회수 낮음", "내용"));
        Post high = postRepository.saveAndFlush(createPost("조회수 높음", "내용"));
        postRepository.incrementViewCount(high.getId()); // high: viewCount 1, low: 0
        entityManager.flush();
        entityManager.clear();

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "viewCount"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        assertThat(result.getContent())
                .extracting(PostListResponse::id)
                .containsExactly(high.getId(), low.getId());
        assertThat(result.getContent())
                .extracting(PostListResponse::viewCount)
                .containsExactly(1, 0); // 순서가 삽입순이 아니라 실제 viewCount 값(1 > 0)으로 결정됨을 확정
    }

    @Test
    @DisplayName("화이트리스트 밖 정렬 키(member.password 등)는 무시하고 기본 정렬(createdAt DESC)로 폴백한다 (정렬 주입 방지)")
    void search_ignores_disallowed_sort_property() {
        Post older = postRepository.saveAndFlush(createPost("제목1", "내용1"));
        Post newer = postRepository.saveAndFlush(createPost("제목2", "내용2"));
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        setCreatedAt(older.getId(), base);
        setCreatedAt(newer.getId(), base.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        // 허용 컬럼이 아닌 임의 경로로 정렬을 시도해도 UnknownPathException 등으로 깨지지 않고 기본 정렬로 폴백해야 한다
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "member.password"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        // 폴백이 createdAt DESC이므로 최신(newer)이 먼저. 폴백이 빠지거나 삽입순이면 순서가 어긋나 깨진다.
        assertThat(result.getContent())
                .extracting(PostListResponse::id)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
