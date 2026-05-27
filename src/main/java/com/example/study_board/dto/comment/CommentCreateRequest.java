package com.example.study_board.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank(message = "내용은 필수입니다")
        String content,

        @NotBlank(message = "작성자는 필수입니다")
        @Size(max = 50, message = "작성자는 50자 이하여야 합니다")
        String author
) {
}
