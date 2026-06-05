package com.example.study_board.domain.notification;

import com.example.study_board.dto.notification.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSseServiceTest {

    @Mock
    private SseEmitterRepository emitterRepository;

    @InjectMocks
    private NotificationSseService sseService;

    private NotificationResponse sampleData() {
        return new NotificationResponse(1L, NotificationType.COMMENT_ON_POST, "메시지", false, 1L, 5L, null);
    }

    @Test
    @DisplayName("구독 - emitter를 생성해 저장소에 등록한다")
    void subscribe_registers_emitter() {
        SseEmitter emitter = sseService.subscribe(1L);

        assertThat(emitter).isNotNull();
        verify(emitterRepository).add(eq(1L), any(SseEmitter.class));
    }

    @Test
    @DisplayName("푸시 - 수신자의 각 연결로 notification 이벤트를 보낸다")
    void send_pushes_to_each_emitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        given(emitterRepository.get(1L)).willReturn(List.of(emitter));

        sseService.send(1L, sampleData());

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("푸시 - 끊긴 연결(IOException)은 저장소에서 제거한다")
    void send_removes_broken_emitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        given(emitterRepository.get(1L)).willReturn(List.of(emitter));
        doThrow(new IOException("broken")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        sseService.send(1L, sampleData());

        verify(emitterRepository).remove(1L, emitter);
    }
}
