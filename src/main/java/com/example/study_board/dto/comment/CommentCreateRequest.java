package com.example.study_board.dto.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @Schema(description = "댓글 내용", example = "좋은 글이네요!")
        @NotBlank(message = "내용은 필수입니다")
        String content
) {
}
