package com.example.study_board.dto.post;

/**
 * 게시글 동적 검색 조건(Phase 14). 각 필드가 null/blank이면 해당 조건은 무시된다.
 * categoryId는 정확 일치(eq), tag는 태그명 일치(중간 엔티티 join)로 필터링한다.
 */
public record PostSearchCondition(
        String keyword,
        String author,
        Long categoryId,
        String tag
) {
}
