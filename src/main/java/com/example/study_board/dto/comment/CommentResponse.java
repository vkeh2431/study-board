package com.example.study_board.dto.comment;

import com.example.study_board.domain.comment.Comment;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        String content,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getContent(),
                comment.getAuthor(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
