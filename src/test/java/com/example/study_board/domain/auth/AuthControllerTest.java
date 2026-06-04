package com.example.study_board.domain.auth;

import com.example.study_board.domain.member.MemberService;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.RefreshRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.dto.member.MemberResponse;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.security.CustomUserDetailsService;
import com.example.study_board.global.security.JwtProvider;
import com.example.study_board.global.config.SecurityConfig;
import com.example.study_board.global.security.RestAccessDeniedHandler;
import com.example.study_board.global.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("회원가입 성공 시 201과 MemberResponse 반환")
    void signup_returns_201() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "유저", "password123");
        given(memberService.signup(any(SignupRequest.class)))
                .willReturn(new MemberResponse(1L, "user@example.com", "유저", Role.USER));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("회원가입 시 이메일 형식이 잘못되면 400")
    void signup_invalid_email_returns_400() throws Exception {
        SignupRequest request = new SignupRequest("not-an-email", "유저", "password123");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("로그인 성공 시 200과 토큰 반환")
    void login_returns_tokens() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        given(authService.login(any(LoginRequest.class)))
                .willReturn(new TokenResponse("access-token", "refresh-token", "Bearer"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.grantType").value("Bearer"));
    }

    @Test
    @DisplayName("로그인 실패 시 INVALID_CREDENTIALS 401")
    void login_invalid_credentials_returns_401() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "wrong");
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));
    }

    @Test
    @DisplayName("리프레시 토큰으로 새 토큰 발급 200")
    void refresh_returns_tokens() throws Exception {
        RefreshRequest request = new RefreshRequest("refresh-token");
        given(authService.refresh("refresh-token"))
                .willReturn(new TokenResponse("new-access", "new-refresh", "Bearer"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }
}
