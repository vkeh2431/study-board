package com.example.study_board.dto.comment;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(
        @NotBlank(message = "내용은 필수입니다")
        String content
) {
}
