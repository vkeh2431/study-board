package com.example.study_board.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    /**
     * 조회수를 DB에서 원자적으로 1 증가시킨다(lost update 방지, Phase 16).
     *
     * <p>엔티티 로드 후 {@code viewCount++}(dirty checking) 방식은 동시 요청 시 read-modify-write 경합으로
     * 증가분이 유실된다. UPDATE 한 문장으로 DB가 덧셈을 원자적으로 수행하게 해 이를 제거한다.
     *
     * <p>⚠️ {@code @SQLRestriction("deleted_at IS NULL")}은 벌크 JPQL UPDATE에는 적용되지 않으므로
     * {@code AND p.deletedAt IS NULL}을 직접 명시한다(soft-deleted 글 증가 방지).
     * <p>{@code clearAutomatically=true}: 벌크 UPDATE는 영속성 컨텍스트를 우회하므로, 직후 {@code findById}가
     * 1차 캐시의 stale viewCount를 읽지 않도록 컨텍스트를 비운다.
     *
     * @return 영향받은 행 수(0이면 존재하지 않거나 soft-deleted → 404 판단에 사용)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id AND p.deletedAt IS NULL")
    int incrementViewCount(@Param("id") Long id);
}
