package com.example.study_board.domain.like;

import com.example.study_board.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요 추가/취소(Phase 14). 쓰기 동작이라 SecurityConfig의 {@code anyRequest().authenticated()}로 인증 필수.
 * (GET /api/posts/** 만 permitAll이라 별도 설정 불필요)
 */
@Tag(name = "좋아요", description = "게시글 좋아요 추가/취소 (인증 필요)")
@RestController
@RequestMapping("/api/posts/{postId}/likes")
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Operation(summary = "좋아요 추가", description = "게시글에 좋아요를 추가한다. 이미 누른 경우 409.")
    @PostMapping
    public ResponseEntity<Void> like(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        postLikeService.like(postId, principal.getMemberId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "좋아요 취소", description = "게시글의 좋아요를 취소한다.")
    @DeleteMapping
    public ResponseEntity<Void> unlike(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        postLikeService.unlike(postId, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
