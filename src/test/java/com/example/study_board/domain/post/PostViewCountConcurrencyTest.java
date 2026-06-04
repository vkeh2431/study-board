package com.example.study_board.domain.post;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.integration.AbstractMySqlContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회수 동시성(lost update) 통합 테스트.
 *
 * <p><b>왜 실 MySQL인가</b>: 원자적 UPDATE의 동시성 의미는 실제 DB 엔진에서만 증명된다(H2 인메모리는 부정확).
 * 그래서 {@link AbstractMySqlContainerTest}를 상속한다.
 *
 * <p><b>왜 {@code @Transactional}이 없는가</b>: 각 스레드는 자신의 트랜잭션에서 커밋하므로 테스트 트랜잭션
 * 롤백이 닿지 않고, 오히려 커밋된 증가분을 가려 검증이 무의미해진다. 대신 {@link JdbcTemplate} 네이티브
 * DELETE로 수동 정리한다(soft delete를 우회해 행을 실제로 제거).
 */
class PostViewCountConcurrencyTest extends AbstractMySqlContainerTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long postId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        Member member = memberRepository.save(Member.builder()
                .email("viewcount@example.com")
                .username("작성자")
                .password("encoded")
                .role(Role.USER)
                .build());
        postId = postRepository.save(Post.builder()
                .title("동시성")
                .content("내용")
                .member(member)
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    /** soft delete를 우회해 FK 순서대로 행을 실제 삭제(비트랜잭션 테스트라 롤백 불가). */
    private void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM post_tag");
        jdbcTemplate.execute("DELETE FROM post_like");
        jdbcTemplate.execute("DELETE FROM comment");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM member");
    }

    @Test
    @DisplayName("50개 스레드가 동시에 조회해도 조회수가 정확히 50 증가한다 (lost update 없음)")
    void concurrent_views_increment_exactly() throws InterruptedException {
        int threadCount = 50;
        // 풀 크기는 threadCount 이상이어야 한다: ready 게이트는 모든 스레드가 동시에 떠서 start를 기다릴 때만
        // 0에 도달한다. 풀이 작으면 일부 작업이 큐에서 대기해 ready가 영원히 0이 되지 않아 데드락된다.
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();                    // 모든 스레드를 동시에 발화
                    postService.findById(postId, null);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(postRepository.findById(postId)).get()
                .extracting(Post::getViewCount)
                .isEqualTo(threadCount);
    }
}
