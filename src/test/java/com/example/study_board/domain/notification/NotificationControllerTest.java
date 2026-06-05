package com.example.study_board.domain.notification;

import com.example.study_board.dto.notification.NotificationResponse;
import com.example.study_board.global.config.SecurityConfig;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import com.example.study_board.global.security.CustomUserDetailsService;
import com.example.study_board.global.security.JwtProvider;
import com.example.study_board.global.security.RestAccessDeniedHandler;
import com.example.study_board.global.security.RestAuthenticationEntryPoint;
import com.example.study_board.global.security.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService sseService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("내 알림 목록 조회 - 200, 미읽음 우선 리스트")
    @WithMockCustomUser
    void findMyNotifications() throws Exception {
        List<NotificationResponse> responses = List.of(
                new NotificationResponse(2L, NotificationType.REPLY_ON_COMMENT, "답글 알림", false, 1L, 11L, LocalDateTime.now()),
                new NotificationResponse(1L, NotificationType.COMMENT_ON_POST, "댓글 알림", true, 1L, 10L, LocalDateTime.now())
        );
        given(notificationService.findMyNotifications(1L)).willReturn(responses);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("REPLY_ON_COMMENT"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].read").value(true));
    }

    @Test
    @DisplayName("인증 없이 알림 목록 조회 시 401")
    void findMyNotifications_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("미읽음 개수 조회 - 200")
    @WithMockCustomUser
    void unreadCount() throws Exception {
        given(notificationService.countUnread(1L)).willReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("알림 읽음 처리 - 204")
    @WithMockCustomUser
    void markAsRead() throws Exception {
        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(1L, 1L);
    }

    @Test
    @DisplayName("타인 알림 읽음 처리 시 403")
    @WithMockCustomUser(memberId = 2L)
    void markAsRead_forbidden() throws Exception {
        willThrow(new ForbiddenException())
                .given(notificationService).markAsRead(eq(1L), eq(2L));

        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("없는 알림 읽음 처리 시 404")
    @WithMockCustomUser
    void markAsRead_not_found() throws Exception {
        willThrow(new ResourceNotFoundException("Notification", 999L))
                .given(notificationService).markAsRead(eq(999L), eq(1L));

        mockMvc.perform(patch("/api/notifications/999/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("SSE 구독 - 인증 시 text/event-stream으로 연결을 연다")
    @WithMockCustomUser
    void subscribe_opens_event_stream() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(sseService.subscribe(1L)).willReturn(emitter);

        MvcResult result = mockMvc.perform(get("/api/notifications/subscribe"))
                .andExpect(request().asyncStarted())
                .andReturn();
        emitter.complete();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        verify(sseService).subscribe(1L);
    }
}
