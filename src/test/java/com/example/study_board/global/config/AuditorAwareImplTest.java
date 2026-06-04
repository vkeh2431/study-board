package com.example.study_board.global.config;

import com.example.study_board.domain.member.Role;
import com.example.study_board.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditorAwareImplTest {

    private final AuditorAwareImpl auditorAware = new AuditorAwareImpl();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(Object principal) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    @DisplayName("인증 정보가 없으면 빈 Optional 반환")
    void returns_empty_when_no_authentication() {
        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("익명 인증이면 빈 Optional 반환")
    void returns_empty_when_anonymous() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(anonymous);
        SecurityContextHolder.setContext(context);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("CustomUserDetails principal이면 memberId 반환")
    void returns_member_id_when_authenticated() {
        CustomUserDetails principal =
                new CustomUserDetails(1L, "user@test.com", "작성자", null, Role.USER);
        setAuthentication(principal);

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).contains(1L);
    }

    @Test
    @DisplayName("principal이 CustomUserDetails가 아니면 빈 Optional 반환 (로그인 진행 중 등)")
    void returns_empty_when_principal_is_not_custom_user_details() {
        setAuthentication("someStringPrincipal");

        Optional<Long> result = auditorAware.getCurrentAuditor();

        assertThat(result).isEmpty();
    }
}
