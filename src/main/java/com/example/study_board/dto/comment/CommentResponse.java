package com.example.study_board.dto.comment;

import com.example.study_board.domain.comment.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CommentResponse(
        @Schema(description = "댓글 ID", example = "10")
        Long id,

        @Schema(description = "댓글이 달린 게시글 ID", example = "1")
        Long postId,

        @Schema(description = "댓글 내용", example = "좋은 글이네요!")
        String content,

        @Schema(description = "작성자 사용자명", example = "honggildong")
        String authorName,

        @Schema(description = "작성 일시")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시")
        LocalDateTime updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getContent(),
                comment.getMember().getUsername(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
