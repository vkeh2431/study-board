package com.example.study_board.domain.post;

import com.example.study_board.domain.category.Category;
import com.example.study_board.domain.category.CategoryRepository;
import com.example.study_board.domain.like.PostLikeRepository;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.member.Role;
import com.example.study_board.domain.tag.Tag;
import com.example.study_board.domain.tag.TagRepository;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.ForbiddenException;
import com.example.study_board.global.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @InjectMocks
    private PostService postService;

    private Member createMember(String username) {
        return Member.builder()
                .email(username + "@example.com")
                .username(username)
                .password("encoded")
                .role(Role.USER)
                .build();
    }

    private Post createPost(String title, String content, String authorName) {
        return Post.builder()
                .title(title)
                .content(content)
                .member(createMember(authorName))
                .build();
    }

    private Post postOwnedBy(Long ownerId) {
        Member author = createMember("작성자");
        ReflectionTestUtils.setField(author, "id", ownerId);
        return Post.builder().title("기존 제목").content("기존 내용").member(author).build();
    }

    @Test
    @DisplayName("검색 조건과 페이지 정보를 repository.search에 위임하고 결과를 그대로 반환한다")
    void findAll_delegates_to_repository_search() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PostSearchCondition condition = new PostSearchCondition("Spring", "작성자", null, null);
        Page<PostListResponse> expected = new PageImpl<>(
                List.of(new PostListResponse(1L, "Spring 입문", "작성자", "스프링", 0, 2L, 5L, LocalDateTime.now())),
                pageable, 1);

        given(postRepository.search(condition, pageable)).willReturn(expected);

        Page<PostListResponse> result = postService.findAll(condition, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Spring 입문");
        assertThat(result.getContent().get(0).commentCount()).isEqualTo(2L);
        assertThat(result.getContent().get(0).likeCount()).isEqualTo(5L);
        verify(postRepository).search(condition, pageable);
    }

    @Test
    @DisplayName("조건 없는 목록 조회도 그대로 위임한다")
    void findAll_without_condition_delegates() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PostSearchCondition condition = new PostSearchCondition(null, null, null, null);
        Page<PostListResponse> empty = new PageImpl<>(List.of(), pageable, 0);

        given(postRepository.search(condition, pageable)).willReturn(empty);

        Page<PostListResponse> result = postService.findAll(condition, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(postRepository).search(condition, pageable);
    }

    @Test
    @DisplayName("게시글 생성 - 인증된 회원이 작성자로 주입된다")
    void create_post() {
        Member member = createMember("작성자");
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, null);
        Post saved = Post.builder().title("제목").content("내용").member(member).build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(postRepository.save(any(Post.class))).willReturn(saved);

        PostResponse response = postService.create(1L, request);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post persisted = captor.getValue();
        assertThat(persisted.getTitle()).isEqualTo("제목");
        assertThat(persisted.getContent()).isEqualTo("내용");
        assertThat(persisted.getMember().getUsername()).isEqualTo("작성자");

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.content()).isEqualTo("내용");
        assertThat(response.authorName()).isEqualTo("작성자");
    }

    @Test
    @DisplayName("게시글 생성 시 카테고리와 태그가 반영된다 (없는 태그는 생성)")
    void create_post_with_category_and_tags() {
        Member member = createMember("작성자");
        Category category = Category.builder().name("스프링").build();
        Tag existing = Tag.builder().name("java").build();
        PostCreateRequest request = new PostCreateRequest("제목", "내용", 5L, List.of("java", "spring"));

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(categoryRepository.findById(5L)).willReturn(Optional.of(category));
        given(tagRepository.findByName("java")).willReturn(Optional.of(existing));
        given(tagRepository.findByName("spring")).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = postService.create(1L, request);

        assertThat(response.categoryName()).isEqualTo("스프링");
        assertThat(response.tagNames()).containsExactlyInAnyOrder("java", "spring");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 생성 시 ResourceNotFoundException")
    void create_post_with_unknown_category() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(createMember("작성자")));
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());
        PostCreateRequest request = new PostCreateRequest("제목", "내용", 99L, null);

        assertThatThrownBy(() -> postService.create(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("게시글 단건 조회 시 조회수 증가")
    void find_post_by_id() {
        Post post = createPost("제목", "내용", "작성자");

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostResponse response = postService.findById(1L, null);

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.viewCount()).isEqualTo(1);
        assertThat(post.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 단건 조회 시 게시글이 없으면 예외 발생")
    void find_post_by_id_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("단건 조회 시 좋아요 수와 현재 사용자의 좋아요 여부가 담긴다")
    void find_post_by_id_with_like_info() {
        Post post = createPost("제목", "내용", "작성자");
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(postLikeRepository.countByPostId(1L)).willReturn(3L);
        given(postLikeRepository.existsByMemberIdAndPostId(7L, 1L)).willReturn(true);

        PostResponse response = postService.findById(1L, 7L);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.liked()).isTrue();
    }

    @Test
    @DisplayName("작성자 본인이 게시글 수정")
    void update_post() {
        Post post = postOwnedBy(1L);
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostResponse response = postService.update(1L, 1L, Role.USER, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 내용");
        assertThat(post.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("게시글 수정 시 게시글이 없으면 예외 발생")
    void update_post_not_found() {
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(999L, 1L, Role.USER, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 수정하면 ForbiddenException")
    void update_post_by_non_owner_forbidden() {
        Post post = postOwnedBy(1L);
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(1L, 2L, Role.USER, request))
                .isInstanceOf(ForbiddenException.class);
        assertThat(post.getTitle()).isEqualTo("기존 제목");
    }

    @Test
    @DisplayName("ADMIN은 타인 게시글도 수정 가능")
    void update_post_by_admin_allowed() {
        Post post = postOwnedBy(1L);
        PostUpdateRequest request = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostResponse response = postService.update(1L, 2L, Role.ADMIN, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(post.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    @DisplayName("작성자 본인이 게시글 삭제")
    void delete_post() {
        Post post = postOwnedBy(1L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L, 1L, Role.USER);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("게시글 삭제 시 게시글이 없으면 예외 발생")
    void delete_post_not_found() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(999L, 1L, Role.USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 삭제하면 ForbiddenException")
    void delete_post_by_non_owner_forbidden() {
        Post post = postOwnedBy(1L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(1L, 2L, Role.USER))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    @DisplayName("ADMIN은 타인 게시글도 삭제 가능")
    void delete_post_by_admin_allowed() {
        Post post = postOwnedBy(1L);

        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        postService.delete(1L, 2L, Role.ADMIN);

        verify(postRepository).delete(post);
    }
}
