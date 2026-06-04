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
    @DisplayName("키워드 + 작성자 조건을 함께 적용")
    void search_by_keyword_and_author() {
        Member other = memberRepository.save(Member.builder()
                .email("other@example.com").username("다른작성자").password("encoded").role(Role.USER).build());
        postRepository.save(createPost("Spring 입문", "내용1"));                                  // 작성자, Spring O
        postRepository.save(Post.builder().title("Spring 심화").content("내용2").member(other).build()); // 다른작성자, Spring O
        postRepository.save(createPost("JPA", "내용3"));                                          // 작성자, Spring X

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result = postRepository.search(new PostSearchCondition("Spring", "작성자", null, null), pageable);

        assertThat(result.getContent()).hasSize(2); // "작성자" + "다른작성자" 모두 username에 "작성자" 포함
        assertThat(result.getContent()).allMatch(post -> post.title().contains("Spring"));
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
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("자바 글");
    }

    @Test
    @DisplayName("여러 태그를 가진 글도 태그 검색 시 한 건으로 집계된다 (distinct)")
    void search_by_tag_no_duplicate() {
        savePostWith("멀티태그 글", null, "java", "spring", "jpa");

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostListResponse> result =
                postRepository.search(new PostSearchCondition(null, null, null, "java"), pageable);

        assertThat(result.getContent()).hasSize(1);
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
}
