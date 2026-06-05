package com.example.study_board.domain.notification;

import com.example.study_board.common.BaseTimeEntity;
import com.example.study_board.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림(Phase 18). "내 글/댓글에 댓글이 달리면" 댓글 커밋 후 비동기로 생성된다.
 *
 * <p>알림은 댓글 생성으로부터 <b>파생되는 read-model</b>이다: 표시에 필요한 문장({@code message})과
 * 이동 대상({@code postId}/{@code commentId})을 생성 시점에 박제해, 조회 때 LAZY 연관을 타지 않는다
 * (작성자명 등은 이미 {@code message}에 담겨 있어 N+1이 없다). {@code postId}/{@code commentId}는
 * 원본이 (soft) 삭제될 수 있어 <b>FK 없이</b> 보관한다.
 *
 * <p>{@code createdBy}(=BaseTimeEntity의 {@code AuditorAware})는 별도 스레드(@Async)·커밋 후 단계라
 * SecurityContext가 없어 NULL이다(회원가입과 동일). 알림을 "누가 유발했는지"는 {@code message}에 박제한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String message;

    // ⚠️ read는 MySQL 예약어 → 컬럼명 is_read로 회피. boolean은 Hibernate 7.2 MySQLDialect에서 BIT으로 매핑된다(V5 참조).
    @Column(name = "is_read", nullable = false)
    private boolean read;

    // 이동 대상 리소스 식별자(비정규화). 알림은 파생 뷰라 원본 삭제와 디커플하기 위해 FK를 두지 않는다.
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "comment_id")
    private Long commentId;

    @Builder
    public Notification(Member recipient, NotificationType type, String message, Long postId, Long commentId) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.postId = postId;
        this.commentId = commentId;
        this.read = false;
    }

    /**
     * 주어진 회원이 이 알림의 수신자인지 판별한다(소유권 인가, Phase 12 패턴).
     * LAZY {@code recipient} 프록시의 식별자는 FK에서 읽히므로 추가 SELECT 없이 비교한다.
     */
    public boolean isRecipient(Long memberId) {
        return memberId != null && recipient != null
                && recipient.getId() != null && recipient.getId().equals(memberId);
    }

    /** 읽음 처리(dirty checking). */
    public void markAsRead() {
        this.read = true;
    }
}
