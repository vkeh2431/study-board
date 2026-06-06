package com.example.study_board.domain.notification;

import com.example.study_board.domain.comment.CommentCreatedEvent;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.notification.NotificationResponse;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationSseService sseService;

    @InjectMocks
    private NotificationService notificationService;

    private Member memberWithId(Long id) {
        Member member = Member.builder()
                .email("user" + id + "@example.com")
                .username("회원" + id)
                .password("encoded")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private CommentCreatedEvent rootEvent(Long postOwnerId, Long actorId) {
        return new CommentCreatedEvent(50L, 1L, "제목", postOwnerId, null, null, actorId, "댓글작성자");
    }

    private CommentCreatedEvent replyEvent(Long postOwnerId, Long parentOwnerId, Long actorId) {
        return new CommentCreatedEvent(51L, 1L, "제목", postOwnerId, 10L, parentOwnerId, actorId, "댓글작성자");
    }

    private Notification notificationOwnedBy(Long id, Long recipientId) {
        Notification notification = Notification.builder()
                .recipient(memberWithId(recipientId))
                .type(NotificationType.COMMENT_ON_POST)
                .message("메시지")
                .postId(1L)
                .commentId(5L)
                .build();
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    @DisplayName("타인 글에 루트 댓글 - 글 주인에게 COMMENT_ON_POST 알림 생성 + SSE 푸시")
    void createForComment_root_notifies_post_owner() {
        given(memberRepository.getReferenceById(100L)).willReturn(memberWithId(100L));
        given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

        notificationService.createForComment(rootEvent(100L, 7L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT_ON_POST);
        assertThat(saved.getPostId()).isEqualTo(1L);
        assertThat(saved.getCommentId()).isEqualTo(50L);
        assertThat(saved.getMessage()).contains("댓글작성자").contains("제목");
        verify(sseService).send(eq(100L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("본인 글에 본인이 댓글 - 알림을 만들지 않는다(본인 예외)")
    void createForComment_self_comment_skips() {
        notificationService.createForComment(rootEvent(7L, 7L));

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(sseService, never()).send(any(), any());
    }

    @Test
    @DisplayName("타인 댓글에 대댓글 - 부모 댓글 주인에게 REPLY_ON_COMMENT 알림 생성")
    void createForComment_reply_notifies_parent_owner() {
        given(memberRepository.getReferenceById(200L)).willReturn(memberWithId(200L));
        given(notificationRepository.save(any(Notification.class))).willAnswer(inv -> inv.getArgument(0));

        notificationService.createForComment(replyEvent(100L, 200L, 7L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.REPLY_ON_COMMENT);
        verify(sseService).send(eq(200L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("본인 댓글에 본인이 대댓글 - 알림을 만들지 않는다(본인 예외)")
    void createForComment_self_reply_skips() {
        notificationService.createForComment(replyEvent(100L, 7L, 7L));

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(sseService, never()).send(any(), any());
    }

    @Test
    @DisplayName("내 알림 목록 - 엔티티가 DTO로 매핑된다")
    void findMyNotifications_maps_to_dto() {
        given(notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(1L))
                .willReturn(List.of(notificationOwnedBy(10L, 1L)));

        List<NotificationResponse> result = notificationService.findMyNotifications(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).type()).isEqualTo(NotificationType.COMMENT_ON_POST);
        assertThat(result.get(0).message()).isEqualTo("메시지");
    }

    @Test
    @DisplayName("읽음 처리 - 수신자 본인이면 read=true")
    void markAsRead_marks() {
        Notification notification = notificationOwnedBy(10L, 1L);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        notificationService.markAsRead(10L, 1L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("읽음 처리 - 알림이 없으면 404")
    void markAsRead_not_found() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("읽음 처리 - 수신자가 아니면 403, 상태 변경 없음")
    void markAsRead_non_recipient_forbidden() {
        Notification notification = notificationOwnedBy(10L, 1L);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(10L, 2L))
                .isInstanceOf(ForbiddenException.class);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("미읽음 개수 - 요청 memberId로 repository 메서드를 호출하고 결과를 그대로 반환한다")
    void countUnread_delegates() {
        given(notificationRepository.countByRecipientIdAndReadFalse(1L)).willReturn(3L);

        long result = notificationService.countUnread(1L);

        // 순수 위임: 올바른 memberId로 올바른 repository 메서드를 호출하고 그 결과를 가공 없이 반환하는지가 계약
        verify(notificationRepository).countByRecipientIdAndReadFalse(1L);
        assertThat(result).isEqualTo(3L);
    }
}
