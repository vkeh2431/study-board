package com.example.study_board.dto.post;

import com.example.study_board.domain.post.Post;

import java.time.LocalDateTime;

public record PostListResponse(
        Long id,
        String title,
        String author,
        int viewCount,
        long commentCount,
        LocalDateTime createdAt
) {
    public static PostListResponse from(Post post) {
        return new PostListResponse(
                post.getId(),
                post.getTitle(),
                post.getAuthor(),
                post.getViewCount(),
                post.getComments().size(),
                post.getCreatedAt()
        );
    }
}
