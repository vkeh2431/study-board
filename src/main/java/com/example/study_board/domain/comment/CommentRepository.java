package com.example.study_board.domain.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"member"})
    List<Comment> findByPostIdOrderByCreatedAtDesc(Long postId);

    /**
     * 대댓글 삭제 정책용 자식 수(Phase 17). 0보다 크면 부모를 tombstone 처리하고, 0이면 실제(하드) soft delete한다.
     *
     * <p>파생 쿼리명({@code countByParentId}) 대신 명시 JPQL을 쓴다 — 엔티티에 {@code getParentId()} 헬퍼가 있어
     * Spring Data가 {@code parentId}를 빈 프로퍼티로 오인해 잘못된 경로({@code c.parentId})를 만들기 때문.
     * {@code @SQLRestriction(deleted_at IS NULL)}이 이 JPQL에도 적용되어 하드 soft delete된 자식은 빠지고
     * 일반/tombstone 자식(deleted_at NULL)만 센다.
     */
    @Query("select count(c) from Comment c where c.parent.id = :parentId")
    long countByParentId(@Param("parentId") Long parentId);
}
