package com.example.study_board.dto.post;

import com.example.study_board.domain.post.Post;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        @Schema(description = "게시글 ID", example = "1")
        Long id,

        @Schema(description = "제목", example = "Spring Boot 게시판 만들기")
        String title,

        @Schema(description = "본문 내용", example = "오늘은 JPA 연관관계를 정리했다.")
        String content,

        @Schema(description = "작성자 사용자명", example = "honggildong")
        String authorName,

        @Schema(description = "카테고리명(없으면 null)", example = "Spring")
        String categoryName,

        @Schema(description = "태그명 목록", example = "[\"Spring\", \"JPA\"]")
        List<String> tagNames,

        @Schema(description = "조회수", example = "42")
        int viewCount,

        @Schema(description = "좋아요 수", example = "7")
        long likeCount,

        @Schema(description = "현재 조회자의 좋아요 여부(비로그인이면 false)", example = "false")
        boolean liked,

        @Schema(description = "작성 일시")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시")
        LocalDateTime updatedAt
) {
    /**
     * @param likeCount 게시글 좋아요 수
     * @param liked     현재 조회자가 좋아요했는지(비로그인이면 false)
     */
    public static PostResponse of(Post post, long likeCount, boolean liked) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMember().getUsername(),
                post.getCategory() != null ? post.getCategory().getName() : null,
                post.getTagNames(),
                post.getViewCount(),
                likeCount,
                liked,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
