package com.example.study_board.domain.comment;

import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "댓글", description = "게시글 댓글 CRUD. 목록은 공개, 쓰기는 인증 필요")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글/대댓글 작성",
            description = "게시글에 댓글을 작성한다. parentId를 지정하면 같은 게시글의 그 댓글에 대댓글이 달린다(무제한 depth). 작성자는 토큰에서 주입된다.")
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse response = commentService.create(postId, principal.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "댓글 목록 조회(트리)",
            description = "게시글의 댓글을 계층 트리로 조회한다. 루트 댓글은 작성일 내림차순, 각 대댓글(replies)은 작성순. 자식이 있는 삭제 댓글은 본문이 '삭제된 댓글입니다'로 가려진 채 트리에 남는다.")
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> findByPostId(@PathVariable Long postId) {
        List<CommentResponse> responses = commentService.findByPostId(postId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "댓글 수정", description = "작성자 본인 또는 ADMIN만 수정 가능. 타인은 403.")
    @PutMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse response = commentService.update(id, principal.getMemberId(), principal.getRole(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "댓글 삭제", description = "작성자 본인 또는 ADMIN만 삭제 가능. 타인은 403.")
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        commentService.delete(id, principal.getMemberId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}
