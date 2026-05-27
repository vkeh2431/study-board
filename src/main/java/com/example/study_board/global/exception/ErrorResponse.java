package com.example.study_board.global.exception;

public record ErrorResponse(
        int status,
        String code,
        String message
) {
}
