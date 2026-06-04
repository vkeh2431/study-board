package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse create(PostCreateRequest request) {
        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .author(request.author())
                .build();
        Post saved = postRepository.save(post);
        log.info("게시글 생성 완료: id={}, author={}", saved.getId(), saved.getAuthor());
        return PostResponse.from(saved);
    }

    @Transactional
    public PostResponse findById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    public Page<PostListResponse> findAll(String keyword, Pageable pageable) {
        log.debug("게시글 목록 조회: keyword={}, page={}", keyword, pageable.getPageNumber());
        Page<Post> posts = (keyword == null || keyword.isBlank())
                ? postRepository.findAll(pageable)
                : postRepository.searchByKeyword(keyword, pageable);
        return posts.map(PostListResponse::from);
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        post.update(request.title(), request.content());
        log.info("게시글 수정 완료: id={}", id);
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        postRepository.delete(post);
        log.info("게시글 삭제 완료: id={}", id);
    }
}
