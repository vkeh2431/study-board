package com.example.study_board.dto.auth;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String grantType
) {
}
