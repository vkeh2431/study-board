package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.global.config.JpaAuditingConfig;
import com.example.study_board.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("commenter@example.com")
                .username("댓글 작성자")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    private Post createPost() {
        return Post.builder()
                .title("제목")
                .content("내용")
                .member(member)
                .build();
    }

    private Comment createComment(Post post, String content) {
        return Comment.builder()
                .content(content)
                .member(member)
                .post(post)
                .build();
    }

    private Comment createReply(Post post, Comment parent, String content) {
        return Comment.builder()
                .content(content)
                .member(member)
                .post(post)
                .parent(parent)
                .build();
    }

    /** created_at을 결정적으로 제어해 정렬 테스트의 타임스탬프 동률(flaky)을 막는다. */
    private void setCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE comment SET created_at = :t WHERE id = :id")
                .setParameter("t", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Test
    @DisplayName("댓글 저장")
    void save_comment() {
        Post post = postRepository.save(createPost());

        Comment saved = commentRepository.save(createComment(post, "댓글 내용"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("댓글 내용");
        assertThat(saved.getMember().getUsername()).isEqualTo("댓글 작성자");
        assertThat(saved.getPost().getId()).isEqualTo(post.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("댓글 단건 조회")
    void findById_comment() {
        Post post = postRepository.save(createPost());
        Comment saved = commentRepository.save(createComment(post, "댓글 내용"));

        Optional<Comment> found = commentRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("댓글 내용");
        assertThat(found.get().getMember().getUsername()).isEqualTo("댓글 작성자");
    }

    @Test
    @DisplayName("게시글 ID로 댓글 목록 조회 - 최신순(createdAt DESC) 정렬")
    void findByPostIdOrderByCreatedAtDesc_comments() {
        Post post = postRepository.save(createPost());
        // 저장 순서(c1,c2,c3)와 createdAt 순서를 일부러 어긋나게 해서, ORDER BY createdAt DESC가
        // 빠지면(=삽입순/임의순 반환) 단언이 깨지도록 한다.
        Comment c1 = commentRepository.save(createComment(post, "가장 오래된 댓글"));
        Comment c2 = commentRepository.save(createComment(post, "가장 최신 댓글"));
        Comment c3 = commentRepository.save(createComment(post, "중간 댓글"));
        entityManager.flush();

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        setCreatedAt(c1.getId(), base);
        setCreatedAt(c2.getId(), base.plusMinutes(2));
        setCreatedAt(c3.getId(), base.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId());

        assertThat(comments).extracting(Comment::getContent)
                .containsExactly("가장 최신 댓글", "중간 댓글", "가장 오래된 댓글");
    }

    @Test
    @DisplayName("댓글 삭제 - soft delete: 조회에서는 제외되지만 행은 보존된다")
    void delete_comment() {
        Post post = postRepository.save(createPost());
        Comment saved = commentRepository.saveAndFlush(createComment(post, "댓글 내용"));
        Long id = saved.getId();

        commentRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        // @SQLRestriction으로 조회에서 제외
        assertThat(commentRepository.findById(id)).isEmpty();
        // 행은 물리적으로 보존
        Long count = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM comment WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("게시글 삭제 시 댓글도 cascade로 함께 soft delete (조회 제외, 행은 deleted_at 설정 후 보존)")
    void delete_post_cascades_to_comments() {
        Post post = postRepository.save(createPost());
        commentRepository.save(createComment(post, "댓글1"));
        commentRepository.save(createComment(post, "댓글2"));
        entityManager.flush();
        entityManager.clear();

        postRepository.deleteById(post.getId());
        entityManager.flush();
        entityManager.clear();

        // 조회에서는 제외
        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId())).isEmpty();
        // 댓글 행은 보존되고 deleted_at이 설정됨 (cascade soft delete)
        Long softDeleted = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM comment WHERE post_id = :postId AND deleted_at IS NOT NULL")
                .setParameter("postId", post.getId())
                .getSingleResult()).longValue();
        assertThat(softDeleted).isEqualTo(2L);
    }

    // === Phase 17: 대댓글(자기참조) ===

    @Test
    @DisplayName("대댓글 저장 - parent FK가 저장되고 getParentId()가 프록시 초기화 없이 부모 id를 반환")
    void save_reply_with_parent() {
        Post post = postRepository.save(createPost());
        Comment parent = commentRepository.save(createComment(post, "부모 댓글"));
        commentRepository.save(createReply(post, parent, "대댓글"));
        entityManager.flush();
        entityManager.clear();

        Comment found = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId()).stream()
                .filter(c -> "대댓글".equals(c.getContent()))
                .findFirst()
                .orElseThrow();

        // getParentId()는 FK에서 부모 식별자를 읽는다(트리 조립용). 같은 게시글의 부모는 결과셋에 함께 로드돼 비교 가능.
        assertThat(found.getParentId()).isEqualTo(parent.getId());
    }

    @Test
    @DisplayName("countByParentId - 살아있는 자식 수를 센다")
    void countByParentId_counts_visible_children() {
        Post post = postRepository.save(createPost());
        Comment parent = commentRepository.save(createComment(post, "부모"));
        commentRepository.save(createReply(post, parent, "자식1"));
        commentRepository.save(createReply(post, parent, "자식2"));

        assertThat(commentRepository.countByParentId(parent.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByParentId - tombstone(deleted=true, deleted_at=NULL) 자식도 카운트에 포함")
    void countByParentId_includes_tombstone_children() {
        Post post = postRepository.save(createPost());
        Comment parent = commentRepository.save(createComment(post, "부모"));
        Comment child = commentRepository.saveAndFlush(createReply(post, parent, "자식"));

        child.markDeleted();
        commentRepository.saveAndFlush(child);
        entityManager.clear();

        assertThat(commentRepository.countByParentId(parent.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("countByParentId - 하드 soft delete된 자식(deleted_at 설정)은 카운트에서 제외")
    void countByParentId_excludes_hard_deleted_children() {
        Post post = postRepository.save(createPost());
        Comment parent = commentRepository.save(createComment(post, "부모"));
        Comment child1 = commentRepository.save(createReply(post, parent, "자식1"));
        commentRepository.save(createReply(post, parent, "자식2"));
        entityManager.flush();

        commentRepository.delete(child1);
        entityManager.flush();
        entityManager.clear();

        assertThat(commentRepository.countByParentId(parent.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("tombstone 댓글은 목록 조회에 계속 노출되고(행 보존, deleted_at NULL) deleted 플래그만 설정됨")
    void tombstone_stays_visible_in_post_list() {
        Post post = postRepository.save(createPost());
        Comment comment = commentRepository.saveAndFlush(createComment(post, "tombstone 대상"));
        Long id = comment.getId();

        comment.markDeleted();
        commentRepository.saveAndFlush(comment);
        entityManager.clear();

        // @SQLRestriction(deleted_at IS NULL)에 안 걸려 목록에 계속 노출
        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId()))
                .extracting(Comment::getId)
                .contains(id);
        // deleted=true 플래그만 설정되고 deleted_at은 여전히 NULL (하드 soft delete가 아님 → 조회에서 숨겨지지 않음)
        Comment reloaded = commentRepository.findById(id).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("게시글 댓글 평면 조회 - 루트와 대댓글이 한 리스트로 함께 반환된다")
    void findByPostId_returns_roots_and_replies_flat() {
        Post post = postRepository.save(createPost());
        Comment root = commentRepository.save(createComment(post, "루트"));
        commentRepository.save(createReply(post, root, "대댓글"));

        List<Comment> flat = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId());

        assertThat(flat).extracting(Comment::getContent)
                .containsExactlyInAnyOrder("루트", "대댓글");
    }
}
