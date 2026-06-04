package com.example.study_board.domain.auth;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.security.CustomUserDetails;
import com.example.study_board.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AuthService authService;

    private Member member() {
        return Member.builder()
                .email("user@example.com")
                .username("앨리스")
                .password("encoded")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("로그인 성공 시 액세스/리프레시 토큰을 발급한다")
    void login_issues_tokens() {
        LoginRequest request = new LoginRequest("user@example.com", "raw-password");
        CustomUserDetails principal = new CustomUserDetails(1L, "user@example.com", "앨리스", "encoded", Role.USER);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(jwtProvider.createAccessToken(1L, "앨리스", Role.USER)).willReturn("access-token");
        given(jwtProvider.createRefreshToken(1L)).willReturn("refresh-token");

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.grantType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("인증 실패 시 INVALID_CREDENTIALS 예외")
    void login_invalid_credentials() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong");
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("유효한 리프레시 토큰으로 새 토큰을 재발급한다")
    void refresh_reissues_tokens() {
        given(jwtProvider.validate("refresh-token")).willReturn(true);
        given(jwtProvider.getType("refresh-token")).willReturn("refresh");
        given(jwtProvider.getMemberId("refresh-token")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member()));
        given(jwtProvider.createAccessToken(any(), any(), any())).willReturn("new-access");
        given(jwtProvider.createRefreshToken(any())).willReturn("new-refresh");

        TokenResponse response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("type이 access인 토큰으로 refresh 호출 시 UNAUTHORIZED 예외")
    void refresh_with_access_token_throws() {
        given(jwtProvider.validate("access-token")).willReturn(true);
        given(jwtProvider.getType("access-token")).willReturn("access");

        assertThatThrownBy(() -> authService.refresh("access-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
