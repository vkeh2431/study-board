package com.example.study_board.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 게시글 목록 응답(Phase 14: QueryDSL DTO projection 전용).
 * {@code PostRepositoryImpl.search}가 QueryDSL {@code Projections.constructor}로 직접 생성하므로
 * 컴포넌트 순서는 projection 인자 순서와 1:1로 일치해야 한다.
 * commentCount·likeCount는 SELECT 절 상관 COUNT 서브쿼리로 인라인되어 게시글 수와 무관하게 쿼리 1건이다.
 * 태그는 컬렉션이라 목록에는 넣지 않고 상세({@code PostResponse})에서만 노출한다.
 */
public record PostListResponse(
        @Schema(description = "게시글 ID", example = "1")
        Long id,

        @Schema(description = "제목", example = "Spring Boot 게시판 만들기")
        String title,

        @Schema(description = "작성자 사용자명", example = "honggildong")
        String authorName,

        @Schema(description = "카테고리명(없으면 null)", example = "Spring")
        String categoryName,

        @Schema(description = "조회수", example = "42")
        int viewCount,

        @Schema(description = "댓글 수", example = "3")
        long commentCount,

        @Schema(description = "좋아요 수", example = "7")
        long likeCount,

        @Schema(description = "작성 일시")
        LocalDateTime createdAt
) implements Serializable {
    // Redis 캐싱(Phase 16) 시 기본 JDK 직렬화를 쓰므로 Serializable. 모든 필드도 직렬화 가능(LocalDateTime 포함).
}
