package com.example.study_board.domain.comment;

import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.global.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("댓글 생성")
    void create_comment() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "작성자");
        CommentResponse response = new CommentResponse(1L, 1L, "댓글 내용", "작성자",
                LocalDateTime.now(), LocalDateTime.now());

        given(commentService.create(eq(1L), any(CommentCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("댓글 내용"))
                .andExpect(jsonPath("$.author").value("작성자"));
    }

    @Test
    @DisplayName("댓글 생성 시 내용이 비어있으면 400 에러")
    void create_comment_validation_fail() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("", "작성자");

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("게시글의 댓글 목록 조회")
    void findByPostId_comments() throws Exception {
        List<CommentResponse> responses = List.of(
                new CommentResponse(2L, 1L, "두 번째", "작성자2",
                        LocalDateTime.now(), LocalDateTime.now()),
                new CommentResponse(1L, 1L, "첫 번째", "작성자1",
                        LocalDateTime.now(), LocalDateTime.now())
        );

        given(commentService.findByPostId(1L)).willReturn(responses);

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("댓글 수정")
    void update_comment() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");
        CommentResponse response = new CommentResponse(1L, 1L, "수정된 내용", "작성자",
                LocalDateTime.now(), LocalDateTime.now());

        given(commentService.update(eq(1L), any(CommentUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 내용"));
    }

    @Test
    @DisplayName("댓글 삭제")
    void delete_comment() throws Exception {
        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isNoContent());

        verify(commentService).delete(1L);
    }

    @Test
    @DisplayName("댓글 생성 시 게시글이 없으면 404")
    void create_comment_post_not_found() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "작성자");

        given(commentService.create(eq(999L), any(CommentCreateRequest.class)))
                .willThrow(new ResourceNotFoundException("Post", 999L));

        mockMvc.perform(post("/api/posts/999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("댓글 생성 시 작성자가 비어있으면 400 에러")
    void create_comment_author_blank_validation_fail() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "");

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("댓글 수정 시 댓글이 없으면 404")
    void update_comment_not_found() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentService.update(eq(999L), any(CommentUpdateRequest.class)))
                .willThrow(new ResourceNotFoundException("Comment", 999L));

        mockMvc.perform(put("/api/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("댓글 수정 시 내용이 비어있으면 400 에러")
    void update_comment_validation_fail() throws Exception {
        CommentUpdateRequest request = new CommentUpdateRequest("");

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
