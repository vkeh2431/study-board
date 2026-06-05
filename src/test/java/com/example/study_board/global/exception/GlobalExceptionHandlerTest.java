package com.example.study_board.global.exception;

import com.example.study_board.domain.post.PostController;
import com.example.study_board.domain.post.PostService;
import com.example.study_board.global.config.SecurityConfig;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler가 클라이언트 입력 오류를 4xx로 일관되게 변환하는지 검증한다(PostController를 통해 advice 경유).
 * 특히 깨진 JSON 본문/경로변수 타입 불일치는 클라이언트 잘못이므로 500이 아니라 400이어야 한다.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("깨진 JSON 본문은 400 BAD_REQUEST로 응답한다 (500 아님)")
    @WithMockCustomUser
    void malformed_json_body_returns_400() throws Exception {
        String brokenJson = "{ \"title\": \"제목\", ";

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brokenJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("경로변수 타입 불일치(숫자 자리에 문자)는 400 BAD_REQUEST로 응답한다 (500 아님)")
    void path_variable_type_mismatch_returns_400() throws Exception {
        mockMvc.perform(get("/api/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }
}
