package com.example.study_board.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

public record UnreadCountResponse(
        @Schema(description = "미읽음 알림 개수", example = "3")
        long count
) {
}
