package com.example.study_board.domain.notification;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 연결(SseEmitter) 인메모리 저장소(Phase 18). memberId 한 명이 여러 탭/기기로 접속할 수 있어 1:N으로 보관한다.
 *
 * <p><b>스레드 세이프</b>가 필수다: 구독은 요청(톰캣) 스레드에서, 푸시는 알림 생성 @Async(notify-*) 스레드에서
 * 동시에 들어온다. 그래서 {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList}를 쓴다.
 *
 * <p>단일 인스턴스 메모리 저장이라 스케일아웃(다중 인스턴스) 시엔 사용자가 붙은 노드에서만 푸시된다 —
 * 실무는 Redis Pub/Sub 등으로 브로드캐스트한다(학습 범위 밖, 면접 포인트).
 */
@Component
public class SseEmitterRepository {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void add(Long memberId, SseEmitter emitter) {
        emitters.computeIfAbsent(memberId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public List<SseEmitter> get(Long memberId) {
        return emitters.getOrDefault(memberId, List.of());
    }

    public void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(memberId);
            }
        }
    }
}
