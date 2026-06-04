package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PostCreateRequest request) {
        PostResponse response = postService.create(principal.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // GET은 permitAll이라 비로그인 접근 가능 → principal이 null일 수 있다(liked=false)
        Long memberId = (principal != null) ? principal.getMemberId() : null;
        PostResponse response = postService.findById(id, memberId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PostListResponse>> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PostSearchCondition condition = new PostSearchCondition(keyword, author, categoryId, tag);
        Page<PostListResponse> responses = postService.findAll(condition, pageable);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request) {
        PostResponse response = postService.update(id, principal.getMemberId(), principal.getRole(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        postService.delete(id, principal.getMemberId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}
