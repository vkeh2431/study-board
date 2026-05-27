package com.example.study_board.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        @NotBlank(message = "작성자는 필수입니다")
        @Size(max = 50, message = "작성자는 50자 이하여야 합니다")
        String author
) {
}
