-- Phase 17: 대댓글(계층형 댓글) — 자기참조 + tombstone
-- parent_id: 인접 리스트 모델의 부모 FK(루트 댓글은 NULL).
-- deleted  : tombstone 플래그. deleted_at(=@SQLRestriction이 숨기는 하드 soft delete)과 구분되는 별개의 축.
--            자식 있는 댓글 삭제 시 행은 남기고 본문만 가린다(트리 유지). 응답에서 "삭제된 댓글입니다"로 마스킹.
-- ⚠️ Hibernate 7.2 MySQLDialect는 Java boolean을 bit로 매핑한다 → validate 통과를 위해 BIT로 선언(BOOLEAN/tinyint(1) 아님).

ALTER TABLE comment ADD COLUMN parent_id BIGINT NULL;
ALTER TABLE comment ADD COLUMN deleted   BIT NOT NULL DEFAULT 0;
ALTER TABLE comment ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id);

CREATE INDEX idx_comment_parent ON comment (parent_id);
