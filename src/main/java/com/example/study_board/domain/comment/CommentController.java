package com.example.study_board.domain.comment;

import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse response = commentService.create(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> findByPostId(@PathVariable Long postId) {
        List<CommentResponse> responses = commentService.findByPostId(postId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse response = commentService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
