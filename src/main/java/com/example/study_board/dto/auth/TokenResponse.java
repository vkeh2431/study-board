package com.example.study_board.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "API 호출에 사용하는 access 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "access 토큰 재발급용 refresh 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String grantType
) {
}
