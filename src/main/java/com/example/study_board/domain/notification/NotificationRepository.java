package com.example.study_board.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 내 알림 목록(Phase 18). 파생 쿼리 한 줄로 <b>미읽음 우선</b>(read ASC: false=0이 먼저), 그 안에서 최신순(createdAt DESC).
     * 수신자명을 응답에 쓰지 않으므로 {@code @EntityGraph} 없이도 N+1이 없다(message에 표시 정보가 박제됨).
     */
    List<Notification> findByRecipientIdOrderByReadAscCreatedAtDesc(Long recipientId);

    /** 미읽음 개수. */
    long countByRecipientIdAndReadFalse(Long recipientId);
}
