package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostListResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
