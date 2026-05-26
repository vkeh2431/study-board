package com.example.study_board.dto.post;

public record PostCreateRequest(
        String title,
        String content,
        String author
) {
}
