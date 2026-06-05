package com.example.study_board.domain.notification;

import com.example.study_board.domain.comment.CommentCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 리스너의 위임 책임만 단위로 검증한다. {@code @Async}/{@code @TransactionalEventListener(AFTER_COMMIT)}의
 * 실제 비동기·커밋 후 동작은 {@code NotificationIntegrationTest}(Testcontainers + Awaitility)에서 e2e로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("CommentCreatedEvent 수신 시 NotificationService.createForComment로 위임한다")
    void handleCommentCreated_delegates_to_service() {
        CommentCreatedEvent event = new CommentCreatedEvent(
                50L, 1L, "제목", 100L, null, null, 7L, "댓글작성자");

        listener.handleCommentCreated(event);

        verify(notificationService).createForComment(event);
    }
}
