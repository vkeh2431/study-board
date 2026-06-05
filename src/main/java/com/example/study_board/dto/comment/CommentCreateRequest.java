package com.example.study_board.dto.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @Schema(description = "댓글 내용", example = "좋은 글이네요!")
        @NotBlank(message = "내용은 필수입니다")
        String content,

        @Schema(description = "부모 댓글 ID (대댓글일 때만 지정). 루트 댓글이면 null/생략. parent는 같은 게시글의 댓글이어야 한다.",
                example = "10", nullable = true)
        Long parentId
) {
    /** 루트 댓글 생성 단축 생성자(parentId=null). 기존 호출부 호환용. */
    public CommentCreateRequest(String content) {
        this(content, null);
    }
}
