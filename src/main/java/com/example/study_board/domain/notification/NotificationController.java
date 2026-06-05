package com.example.study_board.domain.notification;

import com.example.study_board.dto.notification.NotificationResponse;
import com.example.study_board.dto.notification.UnreadCountResponse;
import com.example.study_board.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "알림", description = "내 알림 조회/읽음 처리 + SSE 실시간 구독. 모두 인증 필요(본인 알림만)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    @Operation(summary = "내 알림 목록", description = "미읽음(read=false) 우선, 그 안에서 최신순으로 내 알림을 반환한다.")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> findMyNotifications(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(notificationService.findMyNotifications(principal.getMemberId()));
    }

    @Operation(summary = "미읽음 알림 개수", description = "뱃지 표시용 미읽음 개수.")
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.countUnread(principal.getMemberId())));
    }

    @Operation(summary = "알림 읽음 처리", description = "수신자 본인만 가능. 타인 알림은 403, 없는 알림은 404.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        notificationService.markAsRead(id, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "실시간 알림 구독(SSE)",
            description = "text/event-stream으로 연결을 열어 새 알림을 실시간 수신한다. 브라우저 EventSource는 헤더를 못 실어 토큰 전달에 제약이 있다(curl/HttpClient로 검증).")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails principal) {
        return sseService.subscribe(principal.getMemberId());
    }
}
