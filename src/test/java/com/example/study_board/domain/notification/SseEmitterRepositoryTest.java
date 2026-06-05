package com.example.study_board.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository = new SseEmitterRepository();

    @Test
    @DisplayName("같은 회원의 여러 연결을 1:N으로 보관한다")
    void add_and_get_multiple() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();

        repository.add(1L, first);
        repository.add(1L, second);

        assertThat(repository.get(1L)).containsExactly(first, second);
    }

    @Test
    @DisplayName("등록되지 않은 회원은 빈 리스트를 반환한다")
    void get_unknown_returns_empty() {
        assertThat(repository.get(99L)).isEmpty();
    }

    @Test
    @DisplayName("remove로 연결을 제거하면 더 이상 조회되지 않는다")
    void remove_emitter() {
        SseEmitter emitter = new SseEmitter();
        repository.add(1L, emitter);

        repository.remove(1L, emitter);

        assertThat(repository.get(1L)).isEmpty();
    }
}
