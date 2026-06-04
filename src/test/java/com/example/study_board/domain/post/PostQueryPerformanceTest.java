package com.example.study_board.domain.post;

import com.example.study_board.domain.comment.Comment;
import com.example.study_board.domain.like.PostLike;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.global.config.JpaAuditingConfig;
import com.example.study_board.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class PostQueryPerformanceTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager em;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .email("author@example.com")
                .username("작성자")
                .password("encoded")
                .role(Role.USER)
                .build();
        em.persist(member);
        for (int i = 1; i <= 5; i++) {
            Post post = postRepository.save(Post.builder()
                    .title("제목" + i)
                    .content("내용" + i)
                    .member(member)
                    .build());
            for (int j = 1; j <= 3; j++) {
                em.persist(Comment.builder()
                        .content("댓글" + j)
                        .member(member)
                        .post(post)
                        .build());
            }
            // 각 게시글에 좋아요 1개(작성자) → likeCount 서브쿼리도 채워진 상태로 측정
            em.persist(PostLike.builder().member(member).post(post).build());
        }
        em.flush();
        em.clear();

        statistics = em.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("동적 검색 목록 조회 시 댓글·좋아요 카운트 서브쿼리로 N+1이 발생하지 않는다")
    void search_does_not_trigger_n_plus_1() {
        // pageSize(2) < 전체(5)라 PageableExecutionUtils가 count 쿼리를 생략하지 않는다.
        PageRequest pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostListResponse> posts = postRepository.search(new PostSearchCondition(null, null, null, null), pageable);

        assertThat(posts.getContent()).hasSize(2);
        assertThat(posts.getTotalElements()).isEqualTo(5);
        assertThat(posts.getContent()).allSatisfy(p -> {
            assertThat(p.commentCount()).isEqualTo(3L);
            assertThat(p.likeCount()).isEqualTo(1L);
        });
        assertThat(statistics.getPrepareStatementCount())
                .as("content(댓글+좋아요 상관 서브쿼리 2개 인라인) 1 + count 1 = 2. 게시글 수 N·서브쿼리 수와 무관하게 고정 — Phase 8 'COUNT 서브쿼리 DTO projection' 실증")
                .isEqualTo(2);
    }
}
