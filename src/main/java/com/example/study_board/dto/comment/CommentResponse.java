package com.example.study_board.dto.comment;

import com.example.study_board.domain.comment.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record CommentResponse(
        @Schema(description = "댓글 ID", example = "10")
        Long id,

        @Schema(description = "댓글이 달린 게시글 ID", example = "1")
        Long postId,

        @Schema(description = "부모 댓글 ID (루트 댓글이면 null)", example = "10", nullable = true)
        Long parentId,

        @Schema(description = "댓글 내용. 자식이 있는 댓글이 삭제되면 \"삭제된 댓글입니다\"로 가려진다.", example = "좋은 글이네요!")
        String content,

        @Schema(description = "작성자 사용자명. 삭제된 댓글(tombstone)은 null", example = "honggildong", nullable = true)
        String authorName,

        @Schema(description = "삭제된 댓글 여부(자식이 있어 트리 유지를 위해 본문만 가려진 tombstone)", example = "false")
        boolean deleted,

        @Schema(description = "작성 일시")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시")
        LocalDateTime updatedAt,

        @Schema(description = "대댓글 목록(재귀 트리). 루트 댓글 응답에만 채워진다.")
        List<CommentResponse> replies
) {
    /**
     * 단건 응답(생성/수정)용. 막 생성/수정한 살아있는 댓글이라 tombstone 마스킹이 필요 없고 replies는 비어 있다.
     */
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getMember().getUsername(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                List.of()
        );
    }

    /**
     * 트리 조립용 노드. {@code replies}는 자식을 붙일 수 있도록 가변 리스트로 만든다.
     * tombstone(삭제됨)이면 본문을 가리고 작성자를 숨긴다 — 이때 {@code getMember().getUsername()}을
     * 호출하지 않아 LAZY 프록시 초기화/작성자 노출/N+1을 피한다.
     */
    public static CommentResponse treeNode(Comment comment) {
        boolean masked = comment.isDeleted();
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentId(),
                masked ? "삭제된 댓글입니다" : comment.getContent(),
                masked ? null : comment.getMember().getUsername(),
                comment.isDeleted(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                new ArrayList<>()
        );
    }
}
