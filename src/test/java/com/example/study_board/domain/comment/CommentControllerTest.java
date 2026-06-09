package com.example.study_board.domain.comment;

import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.domain.member.Role;
import com.example.study_board.global.config.SecurityConfig;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import com.example.study_board.global.security.CustomUserDetailsService;
import com.example.study_board.global.security.JwtProvider;
import com.example.study_board.global.security.RestAccessDeniedHandler;
import com.example.study_board.global.security.RestAuthenticationEntryPoint;
import com.example.study_board.global.security.WithMockCustomUser;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("댓글 생성")
    @WithMockCustomUser
    void create_comment() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");
        CommentResponse response = new CommentResponse(1L, 1L, null, "댓글 내용", "작성자", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of());

        given(commentService.create(eq(1L), eq(1L), any(CommentCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("댓글 내용"))
                .andExpect(jsonPath("$.authorName").value("작성자"));

        // 요청 본문이 CommentCreateRequest로 역직렬화되어 서비스로 그대로 전달되어야 한다
        ArgumentCaptor<CommentCreateRequest> captor = ArgumentCaptor.forClass(CommentCreateRequest.class);
        verify(commentService).create(eq(1L), eq(1L), captor.capture());
        CommentCreateRequest captured = captor.getValue();
        assertThat(captured.content()).isEqualTo("댓글 내용");
        assertThat(captured.parentId()).isNull();
    }

    @Test
    @DisplayName("인증 없이 댓글 생성 시 401")
    void create_comment_unauthenticated() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("댓글 생성 시 게시글이 없으면 404")
    @WithMockCustomUser
    void create_comment_post_not_found() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");

        given(commentService.create(eq(999L), eq(1L), any(CommentCreateRequest.class)))
                .willThrow(new ResourceNotFoundException("Post", 999L));

        mockMvc.perform(post("/api/posts/999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("댓글 생성 시 내용이 비어있으면 400 에러")
    @WithMockCustomUser
    void create_comment_validation_fail() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("");

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("댓글 생성 시 내용이 한계를 초과하면 400 에러")
    @WithMockCustomUser
    void create_comment_with_too_long_content_returns_400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("a".repeat(1001));

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("게시글의 댓글 목록 조회")
    void findByPostId_comments() throws Exception {
        List<CommentResponse> responses = List.of(
                new CommentResponse(2L, 1L, null, "두 번째", "작성자2", false,
                        LocalDateTime.now(), LocalDateTime.now(), List.of()),
                new CommentResponse(1L, 1L, null, "첫 번째", "작성자1", false,
                        LocalDateTime.now(), LocalDateTime.now(), List.of())
        );

        given(commentService.findByPostId(1L)).willReturn(responses);

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("댓글 목록 조회 시 게시글이 없으면 404")
    void findByPostId_post_not_found() throws Exception {
        given(commentService.findByPostId(999L))
                .willThrow(new ResourceNotFoundException("Post", 999L));

        mockMvc.perform(get("/api/posts/999/comments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("댓글 수정")
    @WithMockCustomUser
    void update_comment() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");
        CommentResponse response = new CommentResponse(1L, 1L, null, "수정된 내용", "작성자", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of());

        given(commentService.update(eq(1L), eq(1L), eq(Role.USER), any(CommentUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 내용"));

        // 요청 본문이 CommentUpdateRequest로 역직렬화되어 서비스로 그대로 전달되어야 한다
        ArgumentCaptor<CommentUpdateRequest> captor = ArgumentCaptor.forClass(CommentUpdateRequest.class);
        verify(commentService).update(eq(1L), eq(1L), eq(Role.USER), captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("댓글 수정 시 댓글이 없으면 404")
    @WithMockCustomUser
    void update_comment_not_found() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentService.update(eq(999L), eq(1L), eq(Role.USER), any(CommentUpdateRequest.class)))
                .willThrow(new ResourceNotFoundException("Comment", 999L));

        mockMvc.perform(put("/api/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("댓글 수정 시 내용이 비어있으면 400 에러")
    @WithMockCustomUser
    void update_comment_validation_fail() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("");

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 댓글 수정하면 403")
    @WithMockCustomUser(memberId = 2L)
    void update_comment_forbidden() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentService.update(eq(1L), eq(2L), eq(Role.USER), any(CommentUpdateRequest.class)))
                .willThrow(new ForbiddenException());

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("댓글 삭제")
    @WithMockCustomUser
    void delete_comment() throws Exception {
        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isNoContent());

        verify(commentService).delete(1L, 1L, Role.USER);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 댓글 삭제하면 403")
    @WithMockCustomUser(memberId = 2L)
    void delete_comment_forbidden() throws Exception {
        willThrow(new ForbiddenException())
                .given(commentService).delete(1L, 2L, Role.USER);

        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    // === Phase 17: 대댓글 ===

    @Test
    @DisplayName("대댓글 작성 - body의 parentId가 서비스로 전달되고 응답에 parentId가 담긴다")
    @WithMockCustomUser
    void create_reply_passes_parentId() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 10L);
        CommentResponse response = new CommentResponse(11L, 1L, 10L, "대댓글", "작성자", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of());

        given(commentService.create(eq(1L), eq(1L), any(CommentCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(10));

        ArgumentCaptor<CommentCreateRequest> captor = ArgumentCaptor.forClass(CommentCreateRequest.class);
        verify(commentService).create(eq(1L), eq(1L), captor.capture());
        assertThat(captor.getValue().parentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("댓글 목록 조회 - 루트에 중첩된 replies 트리로 직렬화된다")
    void findByPostId_returns_tree_json() throws Exception {
        CommentResponse reply = new CommentResponse(2L, 1L, 1L, "대댓글", "작성자2", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of());
        CommentResponse root = new CommentResponse(1L, 1L, null, "루트", "작성자1", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of(reply));

        given(commentService.findByPostId(1L)).willReturn(List.of(root));

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].replies.length()").value(1))
                .andExpect(jsonPath("$[0].replies[0].content").value("대댓글"))
                .andExpect(jsonPath("$[0].replies[0].parentId").value(1));
    }

    @Test
    @DisplayName("대댓글 작성 - 부모가 다른 게시글의 댓글이면 400(BAD_REQUEST)")
    @WithMockCustomUser
    void create_reply_parent_post_mismatch_400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 10L);

        given(commentService.create(eq(1L), eq(1L), any(CommentCreateRequest.class)))
                .willThrow(new com.example.study_board.global.exception.BusinessException(ErrorCode.BAD_REQUEST));

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("댓글 목록 조회 - tombstone은 본문 마스킹·작성자 null이고 트리는 유지된다")
    void findByPostId_tombstone_json() throws Exception {
        CommentResponse reply = new CommentResponse(2L, 1L, 1L, "살아있는 대댓글", "작성자2", false,
                LocalDateTime.now(), LocalDateTime.now(), List.of());
        CommentResponse tombstone = new CommentResponse(1L, 1L, null, "삭제된 댓글입니다", null, true,
                LocalDateTime.now(), LocalDateTime.now(), List.of(reply));

        given(commentService.findByPostId(1L)).willReturn(List.of(tombstone));

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").value("삭제된 댓글입니다"))
                .andExpect(jsonPath("$[0].authorName").value(nullValue()))
                .andExpect(jsonPath("$[0].replies.length()").value(1))
                .andExpect(jsonPath("$[0].replies[0].content").value("살아있는 대댓글"));
    }
}
