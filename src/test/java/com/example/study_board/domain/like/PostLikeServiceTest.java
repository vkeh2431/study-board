package com.example.study_board.domain.like;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private PostLikeService postLikeService;

    private Post post() {
        Member member = Member.builder().email("a@example.com").username("작성자").password("p").role(Role.USER).build();
        return Post.builder().title("제목").content("내용").member(member).build();
    }

    @Test
    @DisplayName("좋아요 추가 - 정상")
    void like_success() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));
        given(postLikeRepository.existsByMemberIdAndPostId(2L, 1L)).willReturn(false);
        given(memberRepository.getReferenceById(2L)).willReturn(
                Member.builder().email("b@example.com").username("좋아요러").password("p").role(Role.USER).build());

        postLikeService.like(1L, 2L);

        verify(postLikeRepository).save(any(PostLike.class));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요하면 404")
    void like_post_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postLikeService.like(999L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(postLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 좋아요한 게시글에 다시 좋아요하면 409(ALREADY_LIKED)")
    void like_already_liked() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));
        given(postLikeRepository.existsByMemberIdAndPostId(2L, 1L)).willReturn(true);

        assertThatThrownBy(() -> postLikeService.like(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_LIKED);
        verify(postLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 요청으로 유니크 제약 위반 시 409(ALREADY_LIKED)로 변환")
    void like_race_condition_maps_to_409() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));
        given(postLikeRepository.existsByMemberIdAndPostId(2L, 1L)).willReturn(false);
        given(memberRepository.getReferenceById(2L)).willReturn(
                Member.builder().email("b@example.com").username("좋아요러").password("p").role(Role.USER).build());
        given(postLikeRepository.save(any(PostLike.class)))
                .willThrow(new DataIntegrityViolationException("uk_post_like"));

        assertThatThrownBy(() -> postLikeService.like(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_LIKED);
    }

    @Test
    @DisplayName("좋아요 취소 - 멱등하게 삭제 호출")
    void unlike_calls_delete() {
        assertThatCode(() -> postLikeService.unlike(1L, 2L)).doesNotThrowAnyException();

        verify(postLikeRepository).deleteByMemberIdAndPostId(2L, 1L);
    }
}
