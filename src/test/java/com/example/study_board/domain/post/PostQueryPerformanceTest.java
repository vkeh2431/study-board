package com.example.study_board.domain.post;

import com.example.study_board.domain.comment.Comment;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.global.config.JpaAuditingConfig;
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
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class PostQueryPerformanceTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager em;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= 5; i++) {
            Post post = postRepository.save(Post.builder()
                    .title("제목" + i)
                    .content("내용" + i)
                    .author("작성자")
                    .build());
            for (int j = 1; j <= 3; j++) {
                em.persist(Comment.builder()
                        .content("댓글" + j)
                        .author("user")
                        .post(post)
                        .build());
            }
        }
        em.flush();
        em.clear();

        statistics = em.unwrap(Session.class).getSessionFactory().getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("게시글 목록 조회 시 댓글 카운트를 위한 N+1이 발생하지 않는다")
    void findAll_does_not_trigger_n_plus_1() {
        PageRequest pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Post> posts = postRepository.findAll(pageable);
        posts.map(PostListResponse::from).getContent();

        assertThat(posts.getContent()).hasSize(5);
        assertThat(statistics.getPrepareStatementCount())
                .as("페이징 count(1) + Post+comments fetch(1) = 2개여야 한다 (N+1 발생 시 count(1)+findAll(1)+comments(5)=7개)")
                .isEqualTo(2);
    }
}
