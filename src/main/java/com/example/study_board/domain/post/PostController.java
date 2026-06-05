package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "게시글", description = "게시글 CRUD 및 검색. 조회는 공개, 쓰기는 인증 필요")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 작성", description = "인증된 사용자가 게시글을 작성한다. 작성자는 토큰에서 주입된다.")
    @PostMapping
    public ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PostCreateRequest request) {
        PostResponse response = postService.create(principal.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "게시글 단건 조회", description = "게시글 상세를 조회한다. 인증 시 liked 플래그가 채워진다(미인증이면 false).")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> findById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // GET은 permitAll이라 비로그인 접근 가능 → principal이 null일 수 있다(liked=false)
        Long memberId = (principal != null) ? principal.getMemberId() : null;
        PostResponse response = postService.findById(id, memberId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "인기 게시글 목록", description = "조회수 상위 게시글을 Redis에 캐싱하여 반환한다(TTL 5분, 쓰기 시 무효화).")
    @GetMapping("/popular")
    public ResponseEntity<List<PostListResponse>> findPopular() {
        // 정적 경로라 /{id}보다 우선 매칭된다(Spring이 더 구체적인 패턴을 선택).
        return ResponseEntity.ok(postService.findPopular());
    }

    @Operation(summary = "게시글 목록/검색", description = "키워드/작성자/카테고리/태그로 동적 검색하고 페이징한다.")
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

    @Operation(summary = "게시글 수정", description = "작성자 본인 또는 ADMIN만 수정 가능. 타인은 403.")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request) {
        PostResponse response = postService.update(id, principal.getMemberId(), principal.getRole(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 삭제", description = "작성자 본인 또는 ADMIN만 삭제 가능(soft delete). 타인은 403.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        postService.delete(id, principal.getMemberId(), principal.getRole());
        return ResponseEntity.noContent().build();
    }
}
