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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                new PostListResponse(1L, "제목1", "작성자1", null, 0, 0L, 0L, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(any(PostSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("제목1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        // 파라미터 없이 호출하면 @PageableDefault(size=10, createdAt DESC)가 서비스로 전달되어야 한다
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).findAll(any(PostSearchCondition.class), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isZero();
        assertThat(captured.getPageSize()).isEqualTo(10);
        Sort.Order order = captured.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("키워드로 게시글 검색")
    void findAll_with_keyword() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(1L, "Spring Boot 입문", "작성자", null, 0, 0L, 0L, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(any(PostSearchCondition.class), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/posts").param("keyword", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Spring Boot 입문"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // keyword 쿼리 파라미터가 PostSearchCondition으로 바인딩되어 서비스에 전달되어야 한다
        ArgumentCaptor<PostSearchCondition> conditionCaptor = ArgumentCaptor.forClass(PostSearchCondition.class);
        verify(postService).findAll(conditionCaptor.capture(), any(Pageable.class));
        PostSearchCondition captured = conditionCaptor.getValue();
        assertThat(captured.keyword()).isEqualTo("Spring");
        assertThat(captured.author()).isNull();
        assertThat(captured.categoryId()).isNull();
        assertThat(captured.tag()).isNull();
    }

    @Test
    @DisplayName("커스텀 페이지와 사이즈로 조회")
    void findAll_with_custom_page_and_size() throws Exception {
        PageRequest pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(6L, "제목6", "작성자", null, 0, 0L, 0L, LocalDateTime.now())
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

        // page/size 쿼리 파라미터가 Pageable로 바인딩되어 서비스에 전달되어야 한다
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).findAll(any(PostSearchCondition.class), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("게시글 생성")
    @WithMockCustomUser
    void create_post() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", 5L, List.of("Spring", "JPA"));
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

        // 요청 본문이 PostCreateRequest로 역직렬화되어 모든 필드가 서비스로 그대로 전달되어야 한다
        ArgumentCaptor<PostCreateRequest> captor = ArgumentCaptor.forClass(PostCreateRequest.class);
        verify(postService).create(eq(1L), captor.capture());
        PostCreateRequest captured = captor.getValue();
        assertThat(captured.title()).isEqualTo("제목");
        assertThat(captured.content()).isEqualTo("내용");
        assertThat(captured.categoryId()).isEqualTo(5L);
        assertThat(captured.tagNames()).containsExactly("Spring", "JPA");
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
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("게시글 생성 시 제목이 한계를 초과하면 400 에러")
    @WithMockCustomUser
    void create_post_with_too_long_title_returns_400() throws Exception {
        PostCreateRequest request = new PostCreateRequest("a".repeat(201), "내용", null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("게시글 생성 시 본문이 비어있으면 400 에러")
    @WithMockCustomUser
    void create_post_with_blank_content_returns_400() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "", null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("게시글 생성 시 본문이 한계를 초과하면 400 에러")
    @WithMockCustomUser
    void create_post_with_too_long_content_returns_400() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "a".repeat(50001), null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("게시글 생성 시 태그가 10개를 초과하면 400 에러")
    @WithMockCustomUser
    void create_post_with_too_many_tags_returns_400() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, Collections.nCopies(11, "tag"));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.tagNames").exists());
    }

    @Test
    @DisplayName("게시글 생성 시 태그명이 한계를 초과하면 400 에러")
    @WithMockCustomUser
    void create_post_with_too_long_tag_name_returns_400() throws Exception {
        // 원소 단위 제약(List<@Size(max=30) String>)이라 fieldErrors 키가 tagNames[0] 형태로 잡혀
        // 특정 필드 키 대신 status·code만 고정한다
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, List.of("a".repeat(31)));

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
    @DisplayName("인기글 목록 조회 (/popular는 /{id}보다 우선 매칭)")
    void find_popular_posts() throws Exception {
        given(postService.findPopular()).willReturn(List.of(
                new PostListResponse(1L, "인기글", "작성자", null, 100, 5L, 10L, LocalDateTime.now())));

        mockMvc.perform(get("/api/posts/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("인기글"))
                .andExpect(jsonPath("$[0].viewCount").value(100));

        verify(postService).findPopular();
    }

    @Test
    @DisplayName("게시글 수정")
    @WithMockCustomUser
    void update_post() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", 7L, List.of("Spring", "Test"));
        PostResponse response = new PostResponse(1L, "수정된 제목", "수정된 내용", "작성자", null, List.of(), 0, 0L, false,
                LocalDateTime.now(), LocalDateTime.now());

        given(postService.update(eq(1L), eq(1L), eq(Role.USER), any(PostUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.content").value("수정된 내용"));

        // 요청 본문이 PostUpdateRequest로 역직렬화되어 모든 필드가 서비스로 그대로 전달되어야 한다
        ArgumentCaptor<PostUpdateRequest> captor = ArgumentCaptor.forClass(PostUpdateRequest.class);
        verify(postService).update(eq(1L), eq(1L), eq(Role.USER), captor.capture());
        PostUpdateRequest captured = captor.getValue();
        assertThat(captured.title()).isEqualTo("수정된 제목");
        assertThat(captured.content()).isEqualTo("수정된 내용");
        assertThat(captured.categoryId()).isEqualTo(7L);
        assertThat(captured.tagNames()).containsExactly("Spring", "Test");
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
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
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
