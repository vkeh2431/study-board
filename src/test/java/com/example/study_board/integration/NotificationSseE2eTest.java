package com.example.study_board.integration;

import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 18 SSE 실시간 푸시 e2e. 실 포트(RANDOM_PORT)로 SSE 스트림을 열고(JDK HttpClient), 같은 컨텍스트에서
 * 타인이 댓글을 달면 그 알림이 스트림으로 즉시 푸시되는지 확인한다.
 *
 * <p>핵심은 <b>이벤트 기반 decoupling의 보상</b>: 댓글/알림 생성 로직은 그대로인데, 같은 파이프라인에
 * 전달 채널(SSE)만 붙여 실시간 전송이 동작한다. 댓글 트리거는 MockMvc로 보내지만(같은 JVM·같은
 * {@code SseEmitterRepository} 빈), 구독은 실 HTTP라 emitter가 실제 네트워크 스트림에 물려 푸시가 검증된다.
 *
 * <p>H2(test) + 비트랜잭션이라 댓글 POST가 실제 커밋되어 {@code @TransactionalEventListener(AFTER_COMMIT)}가 뜬다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSseE2eTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM post_tag");
        jdbcTemplate.execute("DELETE FROM post_like");
        jdbcTemplate.execute("UPDATE comment SET parent_id = NULL");
        jdbcTemplate.execute("DELETE FROM comment");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM member");
    }

    private String signupAndLogin(String email, String username, String password) throws Exception {
        SignupRequest signup = new SignupRequest(email, username, password);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class).accessToken();
    }

    private Long createPostViaApi(String token, String title, String content) throws Exception {
        PostCreateRequest request = new PostCreateRequest(title, content, null, null);
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PostResponse.class).id();
    }

    private void createCommentViaApi(String token, Long postId, String content) throws Exception {
        CommentCreateRequest request = new CommentCreateRequest(content, null);
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SSE 구독 중 타인이 댓글 - notification 이벤트가 실시간 스트림으로 푸시된다")
    void sse_receives_realtime_notification() throws Exception {
        String tokenA = signupAndLogin("a@example.com", "유저A", "password123");
        String tokenB = signupAndLogin("b@example.com", "유저B", "password123");
        Long postId = createPostViaApi(tokenA, "제목", "내용");

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch notified = new CountDownLatch(1);
        List<String> lines = new CopyOnWriteArrayList<>();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest subscribe = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notifications/subscribe"))
                .header("Authorization", "Bearer " + tokenA)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build();

        // 실 HTTP로 SSE 스트림을 열고, 도착하는 라인을 백그라운드에서 수집한다.
        CompletableFuture<HttpResponse<Stream<String>>> streaming =
                client.sendAsync(subscribe, HttpResponse.BodyHandlers.ofLines());
        streaming.thenAccept(response -> response.body().forEach(line -> {
            lines.add(line);
            if (line.contains("connect")) {
                connected.countDown();
            }
            if (line.contains("COMMENT_ON_POST")) {
                notified.countDown();
            }
        }));

        // 구독이 서버에 등록됐음을 connect 이벤트로 확인한 뒤에 댓글을 단다(푸시 누락 레이스 방지).
        assertThat(connected.await(5, TimeUnit.SECONDS)).as("connect 이벤트 수신").isTrue();

        createCommentViaApi(tokenB, postId, "유저B의 실시간 댓글");

        assertThat(notified.await(5, TimeUnit.SECONDS)).as("notification 푸시 수신").isTrue();
        assertThat(lines).anyMatch(line -> line.contains("event:notification"));

        streaming.cancel(true);
    }
}
