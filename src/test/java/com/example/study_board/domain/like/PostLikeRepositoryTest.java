package com.example.study_board.domain.like;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class PostLikeRepositoryTest {

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    private Member member;
    private Post post;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("liker@example.com").username("좋아요러").password("encoded").role(Role.USER).build());
        post = postRepository.save(Post.builder().title("제목").content("내용").member(member).build());
    }

    @Test
    @DisplayName("좋아요 저장 후 회원·게시글로 존재 여부를 확인한다")
    void exists_by_member_and_post() {
        postLikeRepository.save(PostLike.builder().member(member).post(post).build());

        assertThat(postLikeRepository.existsByMemberIdAndPostId(member.getId(), post.getId())).isTrue();
        assertThat(postLikeRepository.existsByMemberIdAndPostId(999L, post.getId())).isFalse();
    }

    @Test
    @DisplayName("게시글의 좋아요 수를 집계한다")
    void count_by_post() {
        Member other = memberRepository.save(Member.builder()
                .email("other@example.com").username("다른이").password("encoded").role(Role.USER).build());
        postLikeRepository.save(PostLike.builder().member(member).post(post).build());
        postLikeRepository.save(PostLike.builder().member(other).post(post).build());

        assertThat(postLikeRepository.countByPostId(post.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("회원·게시글로 좋아요를 삭제한다")
    void delete_by_member_and_post() {
        postLikeRepository.save(PostLike.builder().member(member).post(post).build());

        postLikeRepository.deleteByMemberIdAndPostId(member.getId(), post.getId());

        assertThat(postLikeRepository.existsByMemberIdAndPostId(member.getId(), post.getId())).isFalse();
    }

    @Test
    @DisplayName("같은 회원이 같은 게시글에 두 번 좋아요하면 유니크 제약 위반")
    void duplicate_like_violates_unique_constraint() {
        postLikeRepository.saveAndFlush(PostLike.builder().member(member).post(post).build());

        assertThatThrownBy(() ->
                postLikeRepository.saveAndFlush(PostLike.builder().member(member).post(post).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
