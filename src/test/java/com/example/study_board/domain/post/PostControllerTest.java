package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.dto.post.PostUpdateRequest;
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
import org.springframework.data.domain.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("기본 페이징으로 게시글 목록 조회")
    void findAll_with_default_pagination() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(1L, "제목1", "작성자1", null, 0, 0L, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(any(PostSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("제목1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("키워드로 게시글 검색")
    void findAll_with_keyword() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(1L, "Spring Boot 입문", "작성자", null, 0, 0L, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(any(PostSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/posts").param("keyword", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 입문"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("커스텀 페이지와 사이즈로 조회")
    void findAll_with_custom_page_and_size() throws Exception {
        PageRequest pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(6L, "제목6", "작성자", null, 0, 0L, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 10);

        given(postService.findAll(any(PostSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/posts")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("제목6"))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("게시글 생성")
    @WithMockCustomUser
    void create_post() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, null);
        PostResponse response = new PostResponse(1L, "제목", "내용", "작성자", null, List.of(), 0, 0L, false,
                LocalDateTime.now(), LocalDateTime.now());

        given(postService.create(eq(1L), any(PostCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.authorName").value("작성자"));
    }

    @Test
    @DisplayName("인증 없이 게시글 생성 시 401")
    void create_post_unauthenticated() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("게시글 생성 시 제목이 비어있으면 400 에러")
    @WithMockCustomUser
    void create_post_validation_fail() throws Exception {
        PostCreateRequest request = new PostCreateRequest("", "내용", null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("게시글 단건 조회")
    void find_post_by_id() throws Exception {
        PostResponse response = new PostResponse(1L, "제목", "내용", "작성자", null, List.of(), 1, 0L, false,
                LocalDateTime.now(), LocalDateTime.now());

        given(postService.findById(eq(1L), any())).willReturn(response);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("게시글 단건 조회 시 게시글이 없으면 404")
    void find_post_by_id_not_found() throws Exception {
        given(postService.findById(eq(999L), any()))
                .willThrow(new ResourceNotFoundException("Post", 999L));

        mockMvc.perform(get("/api/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("게시글 수정")
    @WithMockCustomUser
    void update_post() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);
        PostResponse response = new PostResponse(1L, "수정된 제목", "수정된 내용", "작성자", null, List.of(), 0, 0L, false,
                LocalDateTime.now(), LocalDateTime.now());

        given(postService.update(eq(1L), eq(1L), eq(Role.USER), any(PostUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.content").value("수정된 내용"));
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 수정하면 403")
    @WithMockCustomUser(memberId = 2L)
    void update_post_forbidden() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postService.update(eq(1L), eq(2L), eq(Role.USER), any(PostUpdateRequest.class)))
                .willThrow(new ForbiddenException());

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("게시글 수정 시 제목이 비어있으면 400 에러")
    @WithMockCustomUser
    void update_post_validation_fail() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("", "내용", null, null);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("게시글 수정 시 게시글이 없으면 404")
    @WithMockCustomUser
    void update_post_not_found() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postService.update(eq(999L), eq(1L), eq(Role.USER), any(PostUpdateRequest.class)))
                .willThrow(new ResourceNotFoundException("Post", 999L));

        mockMvc.perform(put("/api/posts/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("게시글 삭제")
    @WithMockCustomUser
    void delete_post() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isNoContent());

        verify(postService).delete(1L, 1L, Role.USER);
    }

    @Test
    @DisplayName("게시글 삭제 시 게시글이 없으면 404")
    @WithMockCustomUser
    void delete_post_not_found() throws Exception {
        willThrow(new ResourceNotFoundException("Post", 999L))
                .given(postService).delete(999L, 1L, Role.USER);

        mockMvc.perform(delete("/api/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 삭제하면 403")
    @WithMockCustomUser(memberId = 2L)
    void delete_post_forbidden() throws Exception {
        willThrow(new ForbiddenException())
                .given(postService).delete(1L, 2L, Role.USER);

        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }
}
