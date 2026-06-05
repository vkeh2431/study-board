package com.example.study_board.integration;

import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.notification.Notification;
import com.example.study_board.domain.notification.NotificationRepository;
import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 18 알림 e2e. 실 MySQL 8.4(Testcontainers) + Flyway V1~V5 + {@code ddl-auto=validate}에서 돈다.
 * 컨텍스트 로드만으로 V5(notification: is_read BIT, recipient FK)와 엔티티의 스키마 일치를 자동 검증한다.
 *
 * <p><b>왜 {@code @Transactional}이 없는가</b>: 알림은 댓글 {@code @TransactionalEventListener(AFTER_COMMIT)} +
 * {@code @Async}로 생성된다. 테스트가 트랜잭션이면 댓글이 커밋되지 않아 AFTER_COMMIT이 영원히 안 뜬다.
 * 그래서 비트랜잭션으로 실제 커밋을 일으키고, 비동기 생성은 {@link org.awaitility.Awaitility}로 기다린다.
 * 정리는 {@link JdbcTemplate} 네이티브 DELETE(soft delete 우회).
 */
class NotificationIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

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

    /** soft delete를 우회해 FK 순서대로 행을 실제 삭제(비트랜잭션이라 롤백 불가). notification은 member를 참조하므로 먼저. */
    private void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM post_tag");
        jdbcTemplate.execute("DELETE FROM post_like");
        // 대댓글 자기참조(parent_id, Phase 17)를 먼저 끊어야 DELETE FROM comment가 self-FK 위반 없이 한 번에 지운다.
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

    private Long memberId(String email) {
        return memberRepository.findByEmail(email).orElseThrow().getId();
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

    private Long createCommentViaApi(String token, Long postId, String content, Long parentId) throws Exception {
        CommentCreateRequest request = new CommentCreateRequest(content, parentId);
        MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CommentResponse.class).id();
    }

    @Test
    @DisplayName("타인이 내 글에 댓글 - 글 주인에게 COMMENT_ON_POST 알림이 비동기로 생성된다")
    void other_comment_creates_notification_for_post_owner() throws Exception {
        String tokenA = signupAndLogin("a@example.com", "유저A", "password123");
        String tokenB = signupAndLogin("b@example.com", "유저B", "password123");
        Long userAId = memberId("a@example.com");
        Long postId = createPostViaApi(tokenA, "제목", "내용");

        createCommentViaApi(tokenB, postId, "유저B의 댓글", null);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(userAId)).hasSize(1));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMMENT_ON_POST"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].postId").value(postId));
    }

    @Test
    @DisplayName("본인 글에 본인이 댓글 - 알림이 생성되지 않는다(본인 예외)")
    void self_comment_creates_no_notification() throws Exception {
        String tokenA = signupAndLogin("a@example.com", "유저A", "password123");
        Long userAId = memberId("a@example.com");
        Long postId = createPostViaApi(tokenA, "제목", "내용");

        createCommentViaApi(tokenA, postId, "내 글에 내 댓글", null);

        // 부재 검증: 1초 기다린 뒤에도 알림이 없어야 한다(비동기가 돌 시간을 주고 0을 확인).
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(userAId)).isEmpty());
    }

    @Test
    @DisplayName("타인이 내 댓글에 대댓글 - 부모 댓글 주인에게 REPLY_ON_COMMENT 알림이 생성된다")
    void reply_creates_notification_for_parent_owner() throws Exception {
        String tokenA = signupAndLogin("a@example.com", "유저A", "password123");
        String tokenB = signupAndLogin("b@example.com", "유저B", "password123");
        Long userAId = memberId("a@example.com");
        Long postId = createPostViaApi(tokenB, "제목", "내용"); // 게시글은 B가 작성
        Long rootByA = createCommentViaApi(tokenA, postId, "유저A의 루트 댓글", null);

        createCommentViaApi(tokenB, postId, "유저B의 대댓글", rootByA);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(userAId))
                        .singleElement()
                        .satisfies(n -> assertThat(n.getType().name()).isEqualTo("REPLY_ON_COMMENT")));
    }

    @Test
    @DisplayName("알림 읽음 처리 - 수신자 본인은 204, 타인은 403")
    void mark_as_read_owner_and_forbidden() throws Exception {
        String tokenA = signupAndLogin("a@example.com", "유저A", "password123");
        String tokenB = signupAndLogin("b@example.com", "유저B", "password123");
        Long userAId = memberId("a@example.com");
        Long postId = createPostViaApi(tokenA, "제목", "내용");
        createCommentViaApi(tokenB, postId, "유저B의 댓글", null);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(userAId)).hasSize(1));
        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(userAId);
        Long notificationId = notifications.get(0).getId();

        // 타인(B)은 403
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 본인(A)은 204, 이후 read=true
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(true));
    }
}
