-- Phase 18: 알림(이벤트 기반 + 비동기). "내 글/댓글에 댓글이 달리면" 댓글 커밋 후 비동기로 생성된다.
-- is_read : read는 MySQL 예약어라 컬럼명을 is_read로 둔다.
--           ⚠️ Hibernate 7.2 MySQLDialect는 Java boolean을 bit로 매핑 → validate 통과를 위해 BIT로 선언(BOOLEAN/tinyint(1) 아님, V4와 동일).
-- post_id/comment_id : 이동 대상 리소스(비정규화 read-model). 원본 (soft)삭제와 디커플하기 위해 FK를 두지 않는다.
-- created_by/last_modified_by : @Async·AFTER_COMMIT라 SecurityContext가 없어 NULL로 남는다(회원가입과 동일).

CREATE TABLE notification (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id     BIGINT       NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    message          VARCHAR(255) NOT NULL,
    is_read          BIT          NOT NULL DEFAULT 0,
    post_id          BIGINT,
    comment_id       BIGINT,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notification_recipient ON notification (recipient_id);
