package com.example.study_board.domain.notification;

import com.example.study_board.domain.comment.CommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 댓글 생성 이벤트를 받아 알림을 만드는 리스너(Phase 18). 발행자(CommentService)와 알림 로직의 결합을 끊는 경계다.
 *
 * <p><b>{@code @TransactionalEventListener(AFTER_COMMIT)}</b> — 댓글 트랜잭션이 <b>커밋된 뒤에만</b> 알림을 만든다.
 * 댓글이 롤백되면 이벤트도 폐기돼 "유령 알림"이 생기지 않는다.
 *
 * <p><b>{@code @Async("notificationExecutor")}</b> — 알림 생성을 별도 스레드로 빼 댓글 응답을 지연시키지 않는다.
 * 동시에 함정을 푼다: 동일 스레드 AFTER_COMMIT 단계는 트랜잭션이 이미 끝나 거기서의 DB 쓰기가 커밋되지 않지만,
 * {@code @Async}로 <b>새 스레드</b>에 들어가면 {@link NotificationService#createForComment}의 {@code @Transactional}이
 * 깨끗한 새 트랜잭션을 연다.
 *
 * <p>이벤트는 원시값만 담고 있어(detached 엔티티/LAZY 프록시 없음) 영속성 컨텍스트가 없는 이 스레드에서 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCreated(CommentCreatedEvent event) {
        log.info("CommentCreatedEvent 수신(비동기, thread={}): commentId={}",
                Thread.currentThread().getName(), event.commentId());
        notificationService.createForComment(event);
    }
}
