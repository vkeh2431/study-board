-- Phase 14-2: 카테고리 + 태그(ManyToMany 중간 엔티티)
-- 모든 신규 테이블은 BaseTimeEntity 상속 → 감사 4컬럼(created_at/updated_at/created_by/last_modified_by) 필수.
-- (test는 H2 + Flyway off라 이 DDL은 dev/prod MySQL validate로만 검증된다.)

CREATE TABLE category (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tag (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_tag_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- post.category_id (nullable: 기존 글 호환 + 카테고리 미지정 허용)
ALTER TABLE post
    ADD COLUMN category_id BIGINT NULL,
    ADD CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES category (id);

CREATE TABLE post_tag (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    post_id          BIGINT      NOT NULL,
    tag_id           BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6),
    created_by       BIGINT,
    last_modified_by BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_tag UNIQUE (post_id, tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post (id),
    CONSTRAINT fk_post_tag_tag  FOREIGN KEY (tag_id)  REFERENCES tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
