package com.example.study_board.global.security;

import com.example.study_board.domain.member.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-study-board-0123456789abcdef";
    private static final long ACCESS_VALIDITY = 3_600_000L;       // 1h
    private static final long REFRESH_VALIDITY = 1_209_600_000L;  // 14d

    private JwtProvider provider(long accessValidity) {
        return new JwtProvider(new JwtProperties(SECRET, accessValidity, REFRESH_VALIDITY));
    }

    @Test
    @DisplayName("액세스 토큰 생성 후 memberId/username/role/type을 추출할 수 있다")
    void access_token_extracts_claims() {
        JwtProvider jwtProvider = provider(ACCESS_VALIDITY);

        String token = jwtProvider.createAccessToken(1L, "앨리스", Role.USER);

        assertThat(jwtProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtProvider.getUsername(token)).isEqualTo("앨리스");
        assertThat(jwtProvider.getRole(token)).isEqualTo("USER");
        assertThat(jwtProvider.getType(token)).isEqualTo("access");
    }

    @Test
    @DisplayName("리프레시 토큰의 type 클레임은 refresh이다")
    void refresh_token_type_is_refresh() {
        JwtProvider jwtProvider = provider(ACCESS_VALIDITY);

        String token = jwtProvider.createRefreshToken(1L);

        assertThat(jwtProvider.getType(token)).isEqualTo("refresh");
        assertThat(jwtProvider.getMemberId(token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효한 토큰은 검증에 성공한다")
    void valid_token_passes_validation() {
        JwtProvider jwtProvider = provider(ACCESS_VALIDITY);

        String token = jwtProvider.createAccessToken(1L, "앨리스", Role.USER);

        assertThat(jwtProvider.validate(token)).isTrue();
    }

    @Test
    @DisplayName("변조된 토큰은 검증에 실패한다")
    void tampered_token_fails_validation() {
        JwtProvider jwtProvider = provider(ACCESS_VALIDITY);
        String token = jwtProvider.createAccessToken(1L, "앨리스", Role.USER);

        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "B" : "A");

        assertThat(jwtProvider.validate(tampered)).isFalse();
    }

    @Test
    @DisplayName("잘못된 서명키로 생성된 토큰은 검증에 실패한다")
    void token_signed_with_other_key_fails_validation() {
        JwtProvider issuer = new JwtProvider(
                new JwtProperties("another-secret-key-totally-different-0123456789", ACCESS_VALIDITY, REFRESH_VALIDITY));
        JwtProvider verifier = provider(ACCESS_VALIDITY);

        String token = issuer.createAccessToken(1L, "앨리스", Role.USER);

        assertThat(verifier.validate(token)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void expired_token_fails_validation() {
        JwtProvider jwtProvider = provider(-1_000L); // 이미 만료된 시점으로 발급

        String token = jwtProvider.createAccessToken(1L, "앨리스", Role.USER);

        assertThat(jwtProvider.validate(token)).isFalse();
    }
}
