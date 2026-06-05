package com.example.study_board.global.security;

import com.example.study_board.domain.member.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private CustomUserDetails principal() {
        return new CustomUserDetails(1L, "user@example.com", "유저", null, Role.USER);
    }

    @Test
    @DisplayName("유효한 access 토큰이면 SecurityContext에 인증을 채운다")
    void valid_access_token_sets_authentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        CustomUserDetails details = principal();
        given(jwtProvider.validate("good-token")).willReturn(true);
        given(jwtProvider.getType("good-token")).willReturn("access");
        given(jwtProvider.getMemberId("good-token")).willReturn(1L);
        given(userDetailsService.loadByMemberId(1L)).willReturn(details);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(details);
        assertThat(chain.getRequest()).isSameAs(request); // 체인은 항상 진행된다
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 체인만 진행한다")
    void no_header_passes_through_unauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 토큰으로 보지 않는다")
    void non_bearer_header_is_ignored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abcdef");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("검증 실패(만료/변조) 토큰은 미인증으로 처리한다")
    void invalid_token_is_unauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tampered");
        given(jwtProvider.validate("tampered")).willReturn(false);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("type=refresh 토큰은 인증에 사용하지 않는다 (access만 허용)")
    void refresh_token_is_rejected_for_authentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer refresh-token");
        given(jwtProvider.validate("refresh-token")).willReturn(true);
        given(jwtProvider.getType("refresh-token")).willReturn("refresh");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("토큰은 유효하나 회원이 없으면 예외를 삼키고 미인증으로 체인을 진행한다")
    void missing_member_is_swallowed_and_chain_proceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer good-token");
        given(jwtProvider.validate("good-token")).willReturn(true);
        given(jwtProvider.getType("good-token")).willReturn("access");
        given(jwtProvider.getMemberId("good-token")).willReturn(99L);
        given(userDetailsService.loadByMemberId(99L)).willThrow(new RuntimeException("no member"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response); // 예외를 삼켜도 체인은 진행된다
    }
}
