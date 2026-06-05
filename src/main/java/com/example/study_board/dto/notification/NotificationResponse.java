package com.example.study_board.dto.notification;

import com.example.study_board.domain.notification.Notification;
import com.example.study_board.domain.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1")
        Long id,

        @Schema(description = "알림 유형", example = "COMMENT_ON_POST")
        NotificationType type,

        @Schema(description = "표시 메시지", example = "honggildong님이 회원님의 게시글 '제목'에 댓글을 남겼습니다.")
        String message,

        @Schema(description = "읽음 여부", example = "false")
        boolean read,

        @Schema(description = "이동 대상 게시글 ID", example = "1", nullable = true)
        Long postId,

        @Schema(description = "관련 댓글 ID", example = "10", nullable = true)
        Long commentId,

        @Schema(description = "생성 일시")
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getPostId(),
                notification.getCommentId(),
                notification.getCreatedAt()
        );
    }
}
