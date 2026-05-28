package com.example.study_board.domain.comment;

import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CommentService commentService;

    private Post createPost() {
        return Post.builder()
                .title("제목")
                .content("내용")
                .author("작성자")
                .build();
    }

    private Comment createComment(Post post) {
        return Comment.builder()
                .content("댓글 내용")
                .author("댓글 작성자")
                .post(post)
                .build();
    }

    @Test
    @DisplayName("댓글 생성")
    void create_comment() {
        Post post = createPost();
        Comment comment = createComment(post);
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "댓글 작성자");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class))).willReturn(comment);

        CommentResponse response = commentService.create(1L, request);

        assertThat(response.content()).isEqualTo("댓글 내용");
        assertThat(response.author()).isEqualTo("댓글 작성자");
        verify(postRepository).findById(1L);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글 생성 시 Post의 comments에도 추가되어 양방향 동기화됨")
    void create_comment_synchronizes_bidirectional() {
        Post post = createPost();
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "댓글 작성자");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.create(1L, request);

        assertThat(post.getComments()).hasSize(1);
        assertThat(post.getComments().get(0).getContent()).isEqualTo("댓글 내용");
        assertThat(post.getComments().get(0).getPost()).isSameAs(post);
    }

    @Test
    @DisplayName("댓글 생성 시 게시글이 없으면 예외 발생")
    void create_comment_post_not_found() {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용", "댓글 작성자");

        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("게시글의 댓글 목록 조회")
    void findByPostId_comments() {
        Post post = createPost();
        Comment comment1 = createComment(post);
        Comment comment2 = createComment(post);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(comment2, comment1));

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).hasSize(2);
        verify(commentRepository).findByPostIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("댓글 수정")
    void update_comment() {
        Post post = createPost();
        Comment comment = createComment(post);
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        CommentResponse response = commentService.update(1L, request);

        assertThat(response.content()).isEqualTo("수정된 내용");
        verify(commentRepository).findById(1L);
    }

    @Test
    @DisplayName("댓글 수정 시 댓글이 없으면 예외 발생")
    void update_comment_not_found() {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("댓글 삭제")
    void delete_comment() {
        Post post = createPost();
        Comment comment = createComment(post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.delete(1L);

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("댓글 삭제 시 댓글이 없으면 예외 발생")
    void delete_comment_not_found() {
        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
