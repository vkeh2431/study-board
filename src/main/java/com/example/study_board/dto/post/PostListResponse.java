package com.example.study_board.dto.post;

import java.time.LocalDateTime;

/**
 * 게시글 목록 응답(Phase 14: QueryDSL DTO projection 전용).
 * {@code PostRepositoryImpl.search}가 QueryDSL {@code Projections.constructor}로 직접 생성하므로
 * 컴포넌트 순서는 projection 인자 순서와 1:1로 일치해야 한다.
 * commentCount·likeCount는 SELECT 절 상관 COUNT 서브쿼리로 인라인되어 게시글 수와 무관하게 쿼리 1건이다.
 * 태그는 컬렉션이라 목록에는 넣지 않고 상세({@code PostResponse})에서만 노출한다.
 */
public record PostListResponse(
        Long id,
        String title,
        String authorName,
        String categoryName,
        int viewCount,
        long commentCount,
        long likeCount,
        LocalDateTime createdAt
) {
}
