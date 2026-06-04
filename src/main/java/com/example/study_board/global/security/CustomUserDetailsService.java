package com.example.study_board.global.security;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * 로그인 시 DaoAuthenticationProvider가 호출. email로 회원을 조회한다.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: " + email));
        return CustomUserDetails.from(member);
    }

    /**
     * JWT 인증 필터가 토큰의 memberId로 principal을 복원할 때 호출.
     */
    public CustomUserDetails loadByMemberId(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: memberId=" + memberId));
        return CustomUserDetails.from(member);
    }
}
