package com.example.study_board.domain.notification;

import com.example.study_board.domain.comment.CommentCreatedEvent;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.dto.notification.NotificationResponse;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    /** 메시지가 VARCHAR(255)를 넘지 않도록 제목을 자르는 상한. */
    private static final int TITLE_LIMIT = 100;

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final NotificationSseService sseService;

    /**
     * 댓글 생성 이벤트로부터 알림을 만든다(Phase 18). {@code NotificationEventListener}가 댓글 <b>커밋 후</b>
     * 별도 스레드(@Async)에서 호출하므로, 여기서 {@code @Transactional}이 깨끗한 새 트랜잭션을 연다.
     *
     * <p><b>수신자 정책(단일)</b>: 루트 댓글이면 게시글 주인에게 {@code COMMENT_ON_POST}, 대댓글이면 부모 댓글
     * 주인에게 {@code REPLY_ON_COMMENT}. (post 주인+parent 주인 양쪽에 다 보내면 깊은 대댓글이 글 주인을 스팸하므로
     * 더 흔한 단일 수신자 정책을 택한다.) <b>본인 예외</b>: 수신자가 작성자 자신이면 알림을 만들지 않는다.
     */
    @Transactional
    public void createForComment(CommentCreatedEvent event) {
        Long recipientId;
        NotificationType type;
        if (event.parentCommentId() == null) {
            recipientId = event.postOwnerId();
            type = NotificationType.COMMENT_ON_POST;
        } else {
            recipientId = event.parentOwnerId();
            type = NotificationType.REPLY_ON_COMMENT;
        }

        if (recipientId == null || recipientId.equals(event.actorId())) {
            log.debug("알림 생략(수신자 없음 또는 본인): recipientId={}, actorId={}", recipientId, event.actorId());
            return;
        }

        Member recipient = memberRepository.getReferenceById(recipientId); // 프록시 — 불필요한 SELECT 회피
        Notification saved = notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(buildMessage(type, event))
                .postId(event.postId())
                .commentId(event.commentId())
                .build());
        log.info("알림 생성: recipientId={}, type={}, commentId={}", recipientId, type, event.commentId());

        // 같은 파이프라인의 전달 채널(SSE). 접속 중이면 실시간 푸시, 아니면 무시(DB에 남아 추후 조회).
        sseService.send(recipientId, NotificationResponse.from(saved));
    }

    public List<NotificationResponse> findMyNotifications(Long memberId) {
        return notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(memberId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAsRead(Long id, Long memberId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        // 404(없음) 먼저, 403(소유권) 다음 — 존재 노출을 피하는 Phase 12 순서.
        if (!notification.isRecipient(memberId)) {
            throw new ForbiddenException();
        }
        notification.markAsRead();
        log.info("알림 읽음 처리: id={}", id);
    }

    public long countUnread(Long memberId) {
        return notificationRepository.countByRecipientIdAndReadFalse(memberId);
    }

    private String buildMessage(NotificationType type, CommentCreatedEvent event) {
        return switch (type) {
            case COMMENT_ON_POST ->
                    "%s님이 회원님의 게시글 '%s'에 댓글을 남겼습니다.".formatted(event.actorName(), shorten(event.postTitle()));
            case REPLY_ON_COMMENT ->
                    "%s님이 회원님의 댓글에 답글을 남겼습니다.".formatted(event.actorName());
        };
    }

    private String shorten(String title) {
        if (title == null) {
            return "";
        }
        return title.length() > TITLE_LIMIT ? title.substring(0, TITLE_LIMIT) + "..." : title;
    }
}
