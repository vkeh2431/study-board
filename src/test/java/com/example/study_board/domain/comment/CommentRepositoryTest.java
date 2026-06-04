package com.example.study_board.domain.comment;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
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
    @DisplayName("게시글 ID로 댓글 목록 조회 - 최신순 정렬")
    void findByPostIdOrderByCreatedAtDesc_comments() {
        Post post = postRepository.save(createPost());
        commentRepository.save(createComment(post, "첫 번째 댓글"));
        commentRepository.save(createComment(post, "두 번째 댓글"));

        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId());

        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).getContent()).isEqualTo("두 번째 댓글");
        assertThat(comments.get(1).getContent()).isEqualTo("첫 번째 댓글");
    }

    @Test
    @DisplayName("댓글 삭제")
    void delete_comment() {
        Post post = postRepository.save(createPost());
        Comment saved = commentRepository.save(createComment(post, "댓글 내용"));

        commentRepository.delete(saved);

        Optional<Comment> found = commentRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("게시글 삭제 시 댓글도 함께 삭제된다")
    void delete_post_cascades_to_comments() {
        Post post = postRepository.save(createPost());
        commentRepository.save(createComment(post, "댓글1"));
        commentRepository.save(createComment(post, "댓글2"));
        entityManager.flush();
        entityManager.clear();

        postRepository.deleteById(post.getId());
        entityManager.flush();

        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(post.getId());
        assertThat(comments).isEmpty();
    }
}
