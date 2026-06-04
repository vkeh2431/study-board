package com.example.study_board.domain.like;

import com.example.study_board.global.config.SecurityConfig;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ResourceNotFoundException;
import com.example.study_board.global.security.CustomUserDetailsService;
import com.example.study_board.global.security.JwtProvider;
import com.example.study_board.global.security.RestAccessDeniedHandler;
import com.example.study_board.global.security.RestAuthenticationEntryPoint;
import com.example.study_board.global.security.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostLikeController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostLikeService postLikeService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("좋아요 추가 - 201")
    @WithMockCustomUser
    void like_post() throws Exception {
        mockMvc.perform(post("/api/posts/1/likes"))
                .andExpect(status().isCreated());

        verify(postLikeService).like(1L, 1L);
    }

    @Test
    @DisplayName("인증 없이 좋아요하면 401")
    void like_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/posts/1/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("이미 좋아요한 게시글이면 409")
    @WithMockCustomUser
    void like_already_liked() throws Exception {
        willThrow(new BusinessException(ErrorCode.ALREADY_LIKED))
                .given(postLikeService).like(1L, 1L);

        mockMvc.perform(post("/api/posts/1/likes"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_LIKED.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요하면 404")
    @WithMockCustomUser
    void like_post_not_found() throws Exception {
        willThrow(new ResourceNotFoundException("Post", 999L))
                .given(postLikeService).like(999L, 1L);

        mockMvc.perform(post("/api/posts/999/likes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("좋아요 취소 - 204")
    @WithMockCustomUser
    void unlike_post() throws Exception {
        mockMvc.perform(delete("/api/posts/1/likes"))
                .andExpect(status().isNoContent());

        verify(postLikeService).unlike(1L, 1L);
    }
}
