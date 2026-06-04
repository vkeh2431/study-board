package com.example.study_board.domain.post;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
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
    private final MemberRepository memberRepository;

    @Transactional
    public PostResponse create(Long memberId, PostCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Post post = Post.builder()
                .title(request.title())
                .content(request.content())
                .member(member)
                .build();
        Post saved = postRepository.save(post);
        log.info("게시글 생성 완료: id={}, author={}", saved.getId(), member.getUsername());
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
    public PostResponse update(Long id, Long memberId, Role role, PostUpdateRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        verifyOwnership(post, memberId, role);
        post.update(request.title(), request.content());
        log.info("게시글 수정 완료: id={}", id);
        return PostResponse.from(post);
    }

    @Transactional
    public void delete(Long id, Long memberId, Role role) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        verifyOwnership(post, memberId, role);
        postRepository.delete(post);
        log.info("게시글 삭제 완료: id={}", id);
    }

    /**
     * 작성자 본인 또는 ADMIN만 통과시킨다(소유권 인가). 그 외에는 403을 던진다.
     * 리소스 존재 확인({@code findById}) 이후에 호출하므로 없는 리소스는 항상 404가 우선한다.
     */
    private void verifyOwnership(Post post, Long memberId, Role role) {
        if (role != Role.ADMIN && !post.isOwner(memberId)) {
            throw new ForbiddenException();
        }
    }
}
