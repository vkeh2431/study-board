package com.example.study_board.global.security;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal.
 * {@link #getUsername()}은 인증 식별자인 email을 반환한다.
 * 화면 표시용 작성자명(Member.username)은 {@link #getNickname()}로 노출한다.
 */
public class CustomUserDetails implements UserDetails {

    private final Long memberId;
    private final String email;
    private final String nickname;
    private final String password;
    private final Role role;

    public CustomUserDetails(Long memberId, String email, String nickname, String password, Role role) {
        this.memberId = memberId;
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.role = role;
    }

    public static CustomUserDetails from(Member member) {
        return new CustomUserDetails(
                member.getId(),
                member.getEmail(),
                member.getUsername(),
                member.getPassword(),
                member.getRole()
        );
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
