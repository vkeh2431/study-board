package com.example.study_board.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다")
        String title,

        @NotBlank(message = "내용은 필수입니다")
        String content,

        // 선택. null이면 카테고리 미지정
        Long categoryId,

        // 선택. null/빈 리스트면 태그 없음
        @Size(max = 10, message = "태그는 최대 10개까지 가능합니다")
        List<@Size(max = 30, message = "태그명은 30자 이하여야 합니다") String> tagNames
) {
}
