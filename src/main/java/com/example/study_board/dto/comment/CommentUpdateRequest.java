package com.example.study_board.dto.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
        @Schema(description = "수정할 댓글 내용(최대 1000자)", example = "내용을 수정합니다.")
        @NotBlank(message = "내용은 필수입니다")
        @Size(max = 1000, message = "댓글은 1000자 이하여야 합니다")
        String content
) {
}
