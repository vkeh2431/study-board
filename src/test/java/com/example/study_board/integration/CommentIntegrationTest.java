package com.example.study_board.integration;

import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 17 대댓글 e2e. 실 MySQL 8.4(Testcontainers) + Flyway V1~V4 + {@code ddl-auto=validate}에서 돈다.
 * 컨텍스트 로드 자체가 V4(parent_id, deleted BIT, self-FK)와 엔티티의 스키마 일치를 자동 검증한다
 * (boolean→bit validate 함정의 1차 관문).
 */
@Transactional
class CommentIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = signupAndLogin("author@example.com", "작성자", "password123");
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
        TokenResponse token = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        return token.accessToken();
    }

    private Long createPostViaApi(String title, String content) throws Exception {
        PostCreateRequest request = new PostCreateRequest(title, content, null, null);
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PostResponse.class).id();
    }

    private Long createCommentViaApi(Long postId, String content, Long parentId) throws Exception {
        CommentCreateRequest request = new CommentCreateRequest(content, parentId);
        MvcResult result = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CommentResponse.class).id();
    }

    @Test
    @DisplayName("대댓글 작성 후 트리 조회 - 무제한 depth(루트>대댓글>대대댓글)가 중첩 트리로 반환된다")
    void create_reply_then_fetch_tree_e2e() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        Long rootId = createCommentViaApi(postId, "루트 댓글", null);
        Long replyId = createCommentViaApi(postId, "대댓글", rootId);
        createCommentViaApi(postId, "대대댓글", replyId);

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("루트 댓글"))
                .andExpect(jsonPath("$[0].replies[0].content").value("대댓글"))
                .andExpect(jsonPath("$[0].replies[0].parentId").value(rootId))
                .andExpect(jsonPath("$[0].replies[0].replies[0].content").value("대대댓글"))
                .andExpect(jsonPath("$[0].replies[0].replies[0].parentId").value(replyId));
    }

    @Test
    @DisplayName("대댓글 작성 - 부모가 다른 게시글의 댓글이면 400(BAD_REQUEST)")
    void create_reply_parent_post_mismatch_400_e2e() throws Exception {
        Long postA = createPostViaApi("A", "내용");
        Long postB = createPostViaApi("B", "내용");
        Long commentOnA = createCommentViaApi(postA, "A의 댓글", null);

        CommentCreateRequest mismatch = new CommentCreateRequest("B에 다는 대댓글", commentOnA);
        mockMvc.perform(post("/api/posts/" + postB + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatch)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("자식 있는 부모 삭제 - 본문만 '삭제된 댓글입니다'로 가려지고(tombstone) 자식 트리는 유지")
    void delete_parent_with_children_tombstones_e2e() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        Long rootId = createCommentViaApi(postId, "곧 삭제될 부모", null);
        createCommentViaApi(postId, "살아남는 대댓글", rootId);

        mockMvc.perform(delete("/api/comments/" + rootId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rootId))
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").value("삭제된 댓글입니다"))
                .andExpect(jsonPath("$[0].authorName").value(nullValue()))
                .andExpect(jsonPath("$[0].replies.length()").value(1))
                .andExpect(jsonPath("$[0].replies[0].content").value("살아남는 대댓글"));
    }

    @Test
    @DisplayName("자식 없는 댓글 삭제 - 트리에서 완전히 사라진다(하드 soft delete)")
    void delete_childless_comment_removes_from_tree_e2e() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        Long commentId = createCommentViaApi(postId, "혼자인 댓글", null);

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
