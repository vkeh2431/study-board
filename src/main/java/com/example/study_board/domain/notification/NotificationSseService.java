package com.example.study_board.domain.notification;

import com.example.study_board.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 알림 SSE 실시간 푸시(Phase 18). 같은 {@code CommentCreatedEvent} 파이프라인에 <b>전달 채널만 추가</b>한 것으로,
 * 댓글/알림 생성 로직은 전혀 건드리지 않는다(이벤트 기반 decoupling의 보상).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSseService {

    /** SSE 연결 타임아웃(30분). 클라이언트는 끊기면 재구독한다. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final SseEmitterRepository emitterRepository;

    /**
     * 현재 사용자의 SSE 스트림을 연다. 등록 후 최초 {@code connect} 이벤트를 1회 보내 연결을 확립한다(프록시 버퍼링 회피).
     * 완료/타임아웃/에러 시 저장소에서 자동 제거한다.
     */
    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitterRepository.add(memberId, emitter);
        emitter.onCompletion(() -> emitterRepository.remove(memberId, emitter));
        emitter.onTimeout(() -> emitterRepository.remove(memberId, emitter));
        emitter.onError(e -> emitterRepository.remove(memberId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitterRepository.remove(memberId, emitter);
        }
        return emitter;
    }

    /**
     * 수신자의 모든 활성 연결로 알림을 푸시한다. 알림 생성 @Async 스레드에서 호출되며, 끊긴 연결은 즉시 정리한다.
     * 미접속이면 저장소가 빈 리스트라 아무 일도 하지 않는다(알림은 이미 DB에 저장돼 추후 조회됨).
     */
    public void send(Long memberId, NotificationResponse data) {
        for (SseEmitter emitter : emitterRepository.get(memberId)) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                emitterRepository.remove(memberId, emitter);
            }
        }
    }
}
