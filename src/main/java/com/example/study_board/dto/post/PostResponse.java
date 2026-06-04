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
        long likeCount,
        boolean liked,
        LocalDateTime createdAt,
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
