package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    private Post postWithId(Long id) {
        Post post = createPost();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    /** 트리 조립 테스트용 댓글: id/createdAt/parent를 직접 부여(영속화 없이). */
    private Comment treeComment(Long id, String content, Post post, Comment parent, LocalDateTime createdAt) {
        Comment comment = Comment.builder().content(content).member(createMember()).post(post).parent(parent).build();
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        return comment;
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
    @DisplayName("게시글의 댓글 목록 조회 - 루트 2개는 대댓글 없는 트리로 반환")
    void findByPostId_comments() {
        Post post = postWithId(1L);
        Comment comment1 = treeComment(1L, "첫 댓글", post, null, LocalDateTime.now());
        Comment comment2 = treeComment(2L, "둘째 댓글", post, null, LocalDateTime.now());

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(comment2, comment1));

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).hasSize(2);
        assertThat(responses).allSatisfy(r -> assertThat(r.replies()).isEmpty());
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

    // === Phase 17: 대댓글 ===

    @Test
    @DisplayName("대댓글 생성 - parentId로 부모를 찾아 parent로 주입한다")
    void create_reply_sets_parent() {
        Post post = postWithId(1L);
        Member member = createMember();
        Comment parent = treeComment(10L, "부모", post, null, LocalDateTime.now());
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 10L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(commentRepository.findById(10L)).willReturn(Optional.of(parent));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.create(1L, 1L, request);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("대댓글 생성 - 부모 댓글이 없으면 404")
    void create_reply_parent_not_found_throws_404() {
        Post post = postWithId(1L);
        Member member = createMember();
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 999L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(commentRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(1L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("대댓글 생성 - 부모가 다른 게시글의 댓글이면 400(BAD_REQUEST)")
    void create_reply_parent_post_mismatch_throws_400() {
        Post post = postWithId(1L);
        Post otherPost = postWithId(2L);
        Member member = createMember();
        Comment parentOnOtherPost = treeComment(10L, "다른 글의 댓글", otherPost, null, LocalDateTime.now());
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 10L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(commentRepository.findById(10L)).willReturn(Optional.of(parentOnOtherPost));

        assertThatThrownBy(() -> commentService.create(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("댓글 트리 조립 - 평면 리스트가 루트+대댓글 트리로 구성된다(깊이 ≥2)")
    void findByPostId_assembles_tree() {
        Post post = postWithId(1L);
        Comment root = treeComment(1L, "루트", post, null, LocalDateTime.now().minusMinutes(2));
        Comment reply = treeComment(2L, "대댓글", post, root, LocalDateTime.now().minusMinutes(1));
        Comment replyOfReply = treeComment(3L, "대대댓글", post, reply, LocalDateTime.now());

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(replyOfReply, reply, root));

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).replies()).hasSize(1);
        assertThat(responses.get(0).replies().get(0).id()).isEqualTo(2L);
        assertThat(responses.get(0).replies().get(0).replies().get(0).id()).isEqualTo(3L);
    }

    @Test
    @DisplayName("댓글 트리 조립 - 부모가 목록에 없는 고아 대댓글은 루트로 승격된다")
    void findByPostId_orphan_reply_promoted_to_root() {
        Post post = postWithId(1L);
        Comment hardDeletedParent = treeComment(99L, "사라진 부모", post, null, LocalDateTime.now());
        Comment orphan = treeComment(2L, "고아 대댓글", post, hardDeletedParent, LocalDateTime.now());

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(orphan)); // 부모(99)는 하드 삭제되어 평면 리스트에 없음

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("댓글 트리 조립 - 루트는 createdAt 내림차순, 대댓글은 작성순(오름차순)")
    void findByPostId_orders_roots_desc_replies_asc() {
        Post post = postWithId(1L);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Comment rootOld = treeComment(1L, "오래된 루트", post, null, base);
        Comment rootNew = treeComment(2L, "최신 루트", post, null, base.plusMinutes(30));
        Comment reply1 = treeComment(3L, "대댓글1", post, rootOld, base.plusMinutes(5));
        Comment reply2 = treeComment(4L, "대댓글2", post, rootOld, base.plusMinutes(10));

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        // DB는 createdAt DESC로 반환: 최신 루트, 대댓글2, 대댓글1, 오래된 루트
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(rootNew, reply2, reply1, rootOld));

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).extracting(CommentResponse::id).containsExactly(2L, 1L); // 루트 DESC
        CommentResponse oldRoot = responses.get(1);
        assertThat(oldRoot.replies()).extracting(CommentResponse::id).containsExactly(3L, 4L); // 대댓글 ASC
    }

    @Test
    @DisplayName("댓글 트리 조립 - tombstone은 본문을 가리고 작성자를 숨긴다(member 접근 없음)")
    void findByPostId_tombstone_masks_content_and_hides_author() {
        Post post = postWithId(1L);
        // tombstone 부모: member를 null로 둬도 마스킹 경로가 getMember()를 호출하지 않음을 검증
        Comment tombstone = Comment.builder().content("원래 내용").member(null).post(post).build();
        ReflectionTestUtils.setField(tombstone, "id", 1L);
        tombstone.markDeleted();
        Comment child = treeComment(2L, "대댓글", post, tombstone, LocalDateTime.now());

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(commentRepository.findByPostIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(tombstone, child));

        List<CommentResponse> responses = commentService.findByPostId(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).deleted()).isTrue();
        assertThat(responses.get(0).content()).isEqualTo("삭제된 댓글입니다");
        assertThat(responses.get(0).authorName()).isNull();
        assertThat(responses.get(0).replies().get(0).content()).isEqualTo("대댓글");
    }

    @Test
    @DisplayName("자식 있는 댓글 삭제 - tombstone 처리(markDeleted), 하드 삭제하지 않음")
    void delete_comment_with_children_tombstones() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));
        given(commentRepository.countByParentId(1L)).willReturn(1L);

        commentService.delete(1L, 1L, Role.USER);

        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("자식 없는 댓글 삭제 - 하드 soft delete(@SQLDelete), tombstone 아님")
    void delete_childless_comment_hard_deletes() {
        Post post = createPost();
        Comment comment = commentOwnedBy(1L, post);

        given(commentRepository.findById(1L)).willReturn(Optional.of(comment));
        given(commentRepository.countByParentId(1L)).willReturn(0L);

        commentService.delete(1L, 1L, Role.USER);

        assertThat(comment.isDeleted()).isFalse();
        verify(commentRepository).delete(comment);
    }

    // === Phase 18: 알림 이벤트 발행 ===

    private Post postWithOwner(Long postId, Long ownerId) {
        Member owner = createMember();
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Post post = Post.builder().title("제목").content("내용").member(owner).build();
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    @Test
    @DisplayName("댓글 생성 - 글 주인/작성자 정보를 담은 CommentCreatedEvent를 발행한다")
    void create_publishes_event() {
        Post post = postWithOwner(1L, 100L);
        Member author = createMember();
        ReflectionTestUtils.setField(author, "id", 7L);
        CommentCreateRequest request = new CommentCreateRequest("댓글");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(7L)).willReturn(Optional.of(author));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> {
            Comment c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 50L);
            return c;
        });

        commentService.create(1L, 7L, request);

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CommentCreatedEvent event = captor.getValue();
        assertThat(event.commentId()).isEqualTo(50L);
        assertThat(event.postId()).isEqualTo(1L);
        assertThat(event.postOwnerId()).isEqualTo(100L);
        assertThat(event.actorId()).isEqualTo(7L);
        assertThat(event.actorName()).isEqualTo("댓글 작성자");
        assertThat(event.parentCommentId()).isNull();
        assertThat(event.parentOwnerId()).isNull();
    }

    @Test
    @DisplayName("대댓글 생성 - 이벤트에 부모 댓글 주인(parentOwnerId)이 담긴다")
    void create_reply_publishes_event_with_parent_owner() {
        Post post = postWithOwner(1L, 100L);
        Member author = createMember();
        ReflectionTestUtils.setField(author, "id", 7L);
        Member parentOwner = createMember();
        ReflectionTestUtils.setField(parentOwner, "id", 200L);
        Comment parent = Comment.builder().content("부모").member(parentOwner).post(post).build();
        ReflectionTestUtils.setField(parent, "id", 10L);
        CommentCreateRequest request = new CommentCreateRequest("대댓글", 10L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(memberRepository.findById(7L)).willReturn(Optional.of(author));
        given(commentRepository.findById(10L)).willReturn(Optional.of(parent));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.create(1L, 7L, request);

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CommentCreatedEvent event = captor.getValue();
        assertThat(event.parentCommentId()).isEqualTo(10L);
        assertThat(event.parentOwnerId()).isEqualTo(200L);
        assertThat(event.postOwnerId()).isEqualTo(100L);
        assertThat(event.actorId()).isEqualTo(7L);
    }
}
