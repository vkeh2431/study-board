package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    private Post createPost(String title, String content, String author) {
        return Post.builder()
                .title(title)
                .content(content)
                .author(author)
                .build();
    }

    @Test
    @DisplayName("키워드 없이 게시글 목록 조회 시 전체 페이징 조회")
    void findAll_without_keyword() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> posts = List.of(createPost("제목1", "내용1", "작성자1"));
        Page<Post> postPage = new PageImpl<>(posts, pageable, 1);

        given(postRepository.findAll(pageable)).willReturn(postPage);

        Page<PostListResponse> result = postService.findAll(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("제목1");
        verify(postRepository).findAll(pageable);
    }

    @Test
    @DisplayName("키워드로 게시글 검색")
    void findAll_with_keyword() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> posts = List.of(createPost("Spring 입문", "내용", "작성자"));
        Page<Post> postPage = new PageImpl<>(posts, pageable, 1);

        given(postRepository.searchByKeyword("Spring", pageable)).willReturn(postPage);

        Page<PostListResponse> result = postService.findAll("Spring", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Spring 입문");
        verify(postRepository).searchByKeyword("Spring", pageable);
    }

    @Test
    @DisplayName("빈 키워드는 전체 조회로 처리")
    void findAll_with_blank_keyword() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> postPage = new PageImpl<>(List.of(), pageable, 0);

        given(postRepository.findAll(pageable)).willReturn(postPage);

        Page<PostListResponse> result = postService.findAll("  ", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(postRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Page<Post>가 Page<PostListResponse>로 변환")
    void findAll_returns_page_of_post_list_response() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Post post = createPost("제목", "내용", "작성자");
        Page<Post> postPage = new PageImpl<>(List.of(post), pageable, 1);

        given(postRepository.findAll(pageable)).willReturn(postPage);

        Page<PostListResponse> result = postService.findAll(null, pageable);

        PostListResponse response = result.getContent().get(0);
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.author()).isEqualTo("작성자");
        assertThat(response.commentCount()).isEqualTo(0);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 생성")
    void create_post() {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", "작성자");
        Post saved = createPost("제목", "내용", "작성자");

        given(postRepository.save(any(Post.class))).willReturn(saved);

        PostResponse response = postService.create(request);

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.content()).isEqualTo("내용");
        assertThat(response.author()).isEqualTo("작성자");
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("게시글 단건 조회 시 조회수 증가")
    void find_post_by_id() {
        Post post = createPost("제목", "내용", "작성자");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostResponse response = postService.findById(1L);

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.viewCount()).isEqualTo(1);
        assertThat(post.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 단건 조회 시 게시글이 없으면 예외 발생")
    void find_post_by_id_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("게시글 수정")
    void update_post() {
        Post post = createPost("기존 제목", "기존 내용", "작성자");
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostResponse response = postService.update(1L, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 내용");
        assertThat(post.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("게시글 수정 시 게시글이 없으면 예외 발생")
    void update_post_not_found() {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용");

        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("게시글 삭제")
    void delete_post() {
        Post post = createPost("제목", "내용", "작성자");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("게시글 삭제 시 게시글이 없으면 예외 발생")
    void delete_post_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
