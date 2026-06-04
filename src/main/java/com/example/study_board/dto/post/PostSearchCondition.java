package com.example.study_board.dto.post;

/**
 * 게시글 동적 검색 조건(Phase 14). 각 필드가 null/blank이면 해당 조건은 무시된다.
 * 14-2에서 categoryId, tag 조건이 추가된다.
 */
public record PostSearchCondition(
        String keyword,
        String author
) {
}
