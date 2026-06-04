package com.example.study_board.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostUpdateRequest(
        @Schema(description = "제목(최대 200자)", example = "Spring Boot 게시판 만들기 (수정)")
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다")
        String title,

        @Schema(description = "본문 내용", example = "내용을 수정합니다.")
        @NotBlank(message = "내용은 필수입니다")
        String content,

        @Schema(description = "카테고리 ID(PUT 전체 교체: null이면 카테고리 해제)", example = "2")
        Long categoryId,

        @Schema(description = "태그명 목록(PUT 전체 교체: null/빈 리스트면 태그 전체 해제)", example = "[\"Spring\", \"Security\"]")
        @Size(max = 10, message = "태그는 최대 10개까지 가능합니다")
        List<@Size(max = 30, message = "태그명은 30자 이하여야 합니다") String> tagNames
) {
}
