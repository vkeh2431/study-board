package com.example.study_board.domain.notification;

/**
 * 알림 유형(Phase 18).
 * <ul>
 *   <li>{@link #COMMENT_ON_POST} — 내 게시글에 댓글이 달림</li>
 *   <li>{@link #REPLY_ON_COMMENT} — 내 댓글에 대댓글이 달림</li>
 * </ul>
 */
public enum NotificationType {
    COMMENT_ON_POST,
    REPLY_ON_COMMENT
}
