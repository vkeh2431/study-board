package com.example.study_board.domain.notification;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.global.config.JpaAuditingConfig;
import com.example.study_board.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private Member recipient;

    @BeforeEach
    void setUp() {
        recipient = memberRepository.save(Member.builder()
                .email("recipient@example.com")
                .username("수신자")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    private Notification createNotification(NotificationType type, String message) {
        return Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .postId(1L)
                .build();
    }

    /** created_at을 결정적으로 제어해 정렬 테스트의 타임스탬프 동률(flaky)을 막는다. */
    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE notification SET created_at = :t WHERE id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Test
    @DisplayName("알림 저장 - 기본값 read=false, 감사 컬럼(createdAt) 자동 설정")
    void save_notification() {
        Notification saved = notificationRepository.save(
                createNotification(NotificationType.COMMENT_ON_POST, "댓글이 달렸습니다"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecipient().getId()).isEqualTo(recipient.getId());
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT_ON_POST);
        assertThat(saved.getMessage()).isEqualTo("댓글이 달렸습니다");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getPostId()).isEqualTo(1L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("내 알림 목록 - 미읽음(read=false)이 먼저, 그 안에서 최신순(createdAt DESC)")
    void find_orders_unread_first_then_newest() {
        Notification older = notificationRepository.save(createNotification(NotificationType.COMMENT_ON_POST, "오래된 미읽음"));
        Notification newer = notificationRepository.save(createNotification(NotificationType.COMMENT_ON_POST, "최신 미읽음"));
        Notification readNewest = notificationRepository.save(createNotification(NotificationType.COMMENT_ON_POST, "읽음(가장 최신이어도 뒤로)"));
        readNewest.markAsRead();
        entityManager.flush();

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        setCreatedAt(older.getId(), base);
        setCreatedAt(newer.getId(), base.plusMinutes(1));
        setCreatedAt(readNewest.getId(), base.plusMinutes(2)); // 가장 최신이지만 읽음이라 맨 뒤여야 함
        entityManager.flush();
        entityManager.clear();

        List<Notification> result = notificationRepository.findByRecipientIdOrderByReadAscCreatedAtDesc(recipient.getId());

        assertThat(result).extracting(Notification::getMessage)
                .containsExactly("최신 미읽음", "오래된 미읽음", "읽음(가장 최신이어도 뒤로)");
        assertThat(result).extracting(Notification::isRead)
                .containsExactly(false, false, true);
    }

    @Test
    @DisplayName("미읽음 개수 - read=false인 알림만 센다")
    void countByRecipientIdAndReadFalse() {
        notificationRepository.save(createNotification(NotificationType.COMMENT_ON_POST, "미읽음1"));
        notificationRepository.save(createNotification(NotificationType.REPLY_ON_COMMENT, "미읽음2"));
        Notification read = notificationRepository.save(createNotification(NotificationType.COMMENT_ON_POST, "읽음"));
        read.markAsRead();
        entityManager.flush();

        assertThat(notificationRepository.countByRecipientIdAndReadFalse(recipient.getId())).isEqualTo(2L);
    }
}
