-- Phase 14-3: 좋아요(PostLike)
-- (member_id, post_id) 유니크 제약이 중복 좋아요/동시 요청 race의 최종 방어선.
-- BaseTimeEntity 상속 → 감사 4컬럼 필수.

CREATE TABLE post_like (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    member_id        BIGINT      NOT NULL,
    post_id          BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_like UNIQUE (member_id, post_id),
    CONSTRAINT fk_post_like_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_post_like_post   FOREIGN KEY (post_id)   REFERENCES post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
