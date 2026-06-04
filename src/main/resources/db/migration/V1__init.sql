-- Phase 13: 누적 초기 스키마 (ddl-auto 의존 탈피, Flyway가 스키마 소유)
-- ddl-auto=validate를 통과해야 하므로 엔티티가 기대하는 컬럼/타입과 정확히 일치해야 한다.
-- 생성 순서: member -> post -> comment (FK 의존 순서)

CREATE TABLE member (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(100) NOT NULL,
    username         VARCHAR(50)  NOT NULL,
    password         VARCHAR(255) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,
    member_id        BIGINT       NOT NULL,
    view_count       INT          NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    deleted_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_post_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comment (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    content          TEXT         NOT NULL,
    member_id        BIGINT       NOT NULL,
    post_id          BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    deleted_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_comment_post   FOREIGN KEY (post_id)   REFERENCES post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
