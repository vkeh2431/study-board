package com.example.study_board.dto.post;

import com.example.study_board.domain.post.Post;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long id,
        String title,
        String content,
        String authorName,
        String categoryName,
        List<String> tagNames,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMember().getUsername(),
                post.getCategory() != null ? post.getCategory().getName() : null,
                post.getTagNames(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
