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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GRANT_TYPE = "Bearer";
    private static final String TYPE_REFRESH = "refresh";

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;

    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        log.info("로그인 성공: memberId={}", principal.getMemberId());
        return issue(principal.getMemberId(), principal.getNickname(), principal.getRole());
    }

    public TokenResponse refresh(String refreshToken) {
        if (!jwtProvider.validate(refreshToken) || !TYPE_REFRESH.equals(jwtProvider.getType(refreshToken))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long memberId = jwtProvider.getMemberId(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return issue(member.getId(), member.getUsername(), member.getRole());
    }

    private TokenResponse issue(Long memberId, String username, Role role) {
        String accessToken = jwtProvider.createAccessToken(memberId, username, role);
        String refreshToken = jwtProvider.createRefreshToken(memberId);
        return new TokenResponse(accessToken, refreshToken, GRANT_TYPE);
    }
}
