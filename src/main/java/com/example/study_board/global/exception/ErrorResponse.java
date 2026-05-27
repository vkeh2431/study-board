package com.example.study_board.global.exception;

import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public ErrorResponse(int status, String code, String message) {
        this(status, code, message, null);
    }
}
