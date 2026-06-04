package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CommentService commentService;

    private Member createMember() {
        return Member.builder()
                .email("commenter@example.com")
                .username("댓글 작성자")
                .password("encoded")
                .role(Role.USER)
                .build();
    }

    private Post createPost() {
        return Post.builder()
                .title("제목")
                .content("내용")
                .member(createMember())
                .build();
    }

    private Comment createComment(Post post) {
        return Comment.builder()
                .content("댓글 내용")
                .member(createMember())
                .post(post)
                .build();
    }

    private Comment commentOwnedBy(Long ownerId, Post post) {
        Member author = createMember();
        ReflectionTestUtils.setField(author, "id", ownerId);
        return Comment.builder().content("댓글 내용").member(author).post(post).build();
    }

    @Test
    @DisplayName("댓글 생성 - 인증된 회원이 작성자로 주입된다")
    void create_comment() {
        Post post = createPost();
        Member member = createMember();
        Comment comment = Comment.builder().content("댓글 내용").member(member).post(post).build();
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(commentRepository.save(any(Comment.class))).willReturn(comment);

        CommentResponse response = commentService.create(1L, 1L, request);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        Comment persisted = captor.getValue();
        assertThat(persisted.getContent()).isEqualTo("댓글 내용");
        assertThat(persisted.getMember().getUsername()).isEqualTo("댓글 작성자");

        assertThat(response.content()).isEqualTo("댓글 내용");
        assertThat(response.authorName()).isEqualTo("댓글 작성자");
        verify(postRepository).findById(1L);
    }

    @Test
    @DisplayName("댓글 생성 시 Post의 comments에도 추가되어 양방향 동기화됨")
    void create_comment_synchronizes_bidirectional() {
        Post post = createPost();
        Member member = createMember();
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.create(1L, 1L, request);

        assertThat(post.getComments()).hasSize(1);
        assertThat(post.getComments().get(0).getContent()).isEqualTo("댓글 내용");
        assertThat(post.getComments().get(0).getPost()).isSameAs(post);
    }

    @Test
    @DisplayName("댓글 생성 시 게시글이 없으면 예외 발생")
    void create_comment_post_not_found() {
        CommentCreateRequest request = new CommentCreateRequest("댓글 내용");

        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(999L, 1L, request))
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
    @DisplayName("댓글 목록 조회 시 게시글이 없으면 예외 발생")
    void findByPostId_post_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.findByPostId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("작성자 본인이 댓글 수정")
    void update_comment() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        CommentResponse response = commentService.update(1L, 1L, Role.USER, request);

        assertThat(response.content()).isEqualTo("수정된 내용");
        verify(commentRepository).findById(1L);
    }

    @Test
    @DisplayName("댓글 수정 시 댓글이 없으면 예외 발생")
    void update_comment_not_found() {
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.update(999L, 1L, Role.USER, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 댓글 수정하면 ForbiddenException")
    void update_comment_by_non_owner_forbidden() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(1L, 2L, Role.USER, request))
                .isInstanceOf(ForbiddenException.class);
        assertThat(comment.getContent()).isEqualTo("댓글 내용");
    }

    @Test
    @DisplayName("ADMIN은 타인 댓글도 수정 가능")
    void update_comment_by_admin_allowed() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);
        CommentUpdateRequest request = new CommentUpdateRequest("수정된 내용");

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        CommentResponse response = commentService.update(1L, 2L, Role.ADMIN, request);

        assertThat(response.content()).isEqualTo("수정된 내용");
        assertThat(comment.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("작성자 본인이 댓글 삭제")
    void delete_comment() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.delete(1L, 1L, Role.USER);

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("댓글 삭제 시 댓글이 없으면 예외 발생")
    void delete_comment_not_found() {
        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(999L, 1L, Role.USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 댓글 삭제하면 ForbiddenException")
    void delete_comment_by_non_owner_forbidden() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, 2L, Role.USER))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("ADMIN은 타인 댓글도 삭제 가능")
    void delete_comment_by_admin_allowed() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));

        commentService.delete(1L, 2L, Role.ADMIN);

        verify(commentRepository).delete(comment);
    }
}
