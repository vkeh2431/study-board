package com.example.study_board.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        // JWT는 가변 길이라 @Size로 한계를 두는 게 무의미하다. 실제 유효성은 서명/만료/타입 검증(JwtProvider)이 책임진다.
        @Schema(description = "로그인 시 발급받은 refresh 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        String refreshToken
) {
}
