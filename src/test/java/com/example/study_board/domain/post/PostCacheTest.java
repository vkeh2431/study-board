package com.example.study_board.domain.post;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기글 Redis 캐싱 동작 검증(Phase 16). 실제 Redis(Testcontainers)로 {@code @Cacheable}/{@code @CacheEvict}를 확인하고,
 * "캐시 히트 시 DB 쿼리가 없다"를 Hibernate {@link Statistics}로 단언한다(Phase 8 기법 재사용).
 *
 * <p>DB는 H2(test 프로필)로 충분하다. 이 클래스만 {@code spring.cache.type=redis}로 덮어써 실제 Redis를 쓴다.
 * 캐시 적재→히트→무효화→재조회의 전 생애주기를 <b>단일 테스트</b>로 검증한다(메서드 간 Redis 상태 공유로 인한
 * flaky를 차단). {@code @BeforeEach}에서 Redis를 FLUSHDB하고 DB는 네이티브 DELETE로 정리한다(비트랜잭션).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.cache.type=redis")
class PostCacheTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        flushRedis();
        cleanDatabase();
        Member member = memberRepository.save(Member.builder()
                .email("cache@example.com")
                .username("작성자")
                .password("encoded")
                .role(Role.USER)
                .build());
        for (int i = 1; i <= 3; i++) {
            postRepository.save(Post.builder().title("인기글" + i).content("내용").member(member).build());
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void tearDown() {
        flushRedis();
        cleanDatabase();
    }

    private void flushRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    /** soft delete를 우회해 FK 순서대로 행을 실제 삭제(비트랜잭션이라 롤백 불가). */
    private void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM post_tag");
        jdbcTemplate.execute("DELETE FROM post_like");
        jdbcTemplate.execute("DELETE FROM comment");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM member");
    }

    @Test
    @DisplayName("인기글 캐싱 생애주기: 1회 DB 조회 → 2회 캐시 히트 → 글 작성 시 무효화 → 재조회")
    void popular_posts_cache_lifecycle() {
        // 1) 첫 조회: 캐시 미스 → DB 조회
        statistics.clear();
        List<PostListResponse> first = postService.findPopular();
        assertThat(first).hasSize(3);
        assertThat(statistics.getPrepareStatementCount())
                .as("1회차: 캐시 미스 → DB 조회").isGreaterThanOrEqualTo(1);

        // 2) 두 번째 조회: Redis 캐시 히트 → DB 미접근
        statistics.clear();
        assertThat(postService.findPopular()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount())
                .as("2회차: Redis 캐시에서 반환 → DB 미접근").isZero();

        // 3) 글 작성 → @CacheEvict로 인기글 캐시 무효화
        Member writer = memberRepository.save(Member.builder()
                .email("writer@example.com")
                .username("작성자2")
                .password("encoded")
                .role(Role.USER)
                .build());
        postService.create(writer.getId(), new PostCreateRequest("새 글", "내용", null, null));

        // 4) 재조회: 캐시가 비워져 DB 재조회 + 새 글 포함(4건)
        statistics.clear();
        List<PostListResponse> afterEvict = postService.findPopular();
        assertThat(afterEvict).as("새 글 포함 4건").hasSize(4);
        assertThat(statistics.getPrepareStatementCount())
                .as("@CacheEvict로 캐시가 비워져 다시 DB 조회").isGreaterThanOrEqualTo(1);
    }
}
