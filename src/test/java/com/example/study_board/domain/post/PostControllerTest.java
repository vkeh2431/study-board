package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("기본 페이징으로 게시글 목록 조회")
    void findAll_with_default_pagination() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostListResponse> content = List.of(
                new PostListResponse(1L, "제목1", "작성자1", 0, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(isNull(), any())).willReturn(page);

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
                new PostListResponse(1L, "Spring Boot 입문", "작성자", 0, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 1);

        given(postService.findAll(eq("Spring"), any())).willReturn(page);

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
                new PostListResponse(6L, "제목6", "작성자", 0, LocalDateTime.now())
        );
        Page<PostListResponse> page = new PageImpl<>(content, pageable, 10);

        given(postService.findAll(isNull(), any())).willReturn(page);

        mockMvc.perform(get("/api/posts")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("제목6"))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}
