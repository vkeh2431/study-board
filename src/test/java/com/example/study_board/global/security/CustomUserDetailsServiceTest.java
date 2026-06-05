package com.example.study_board.global.security;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private Member member(Long id, String email, Role role) {
        Member member = Member.builder()
                .email(email)
                .username("유저")
                .password("encoded")
                .role(role)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("loadUserByUsername: 이메일로 회원을 찾아 principal로 변환한다")
    void loadUserByUsername_returns_principal() {
        given(memberRepository.findByEmail("user@example.com"))
                .willReturn(Optional.of(member(1L, "user@example.com", Role.USER)));

        CustomUserDetails details = (CustomUserDetails) userDetailsService.loadUserByUsername("user@example.com");

        assertThat(details.getMemberId()).isEqualTo(1L);
        assertThat(details.getUsername()).isEqualTo("user@example.com"); // 인증 식별자는 email
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername: 없는 이메일이면 UsernameNotFoundException")
    void loadUserByUsername_throws_when_absent() {
        given(memberRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("none@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("loadByMemberId: memberId로 principal을 복원하고 ADMIN 권한을 반영한다")
    void loadByMemberId_returns_principal_with_role() {
        given(memberRepository.findById(7L))
                .willReturn(Optional.of(member(7L, "admin@example.com", Role.ADMIN)));

        CustomUserDetails details = userDetailsService.loadByMemberId(7L);

        assertThat(details.getMemberId()).isEqualTo(7L);
        assertThat(details.getRole()).isEqualTo(Role.ADMIN);
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadByMemberId: 없는 memberId면 UsernameNotFoundException")
    void loadByMemberId_throws_when_absent() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadByMemberId(999L))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
