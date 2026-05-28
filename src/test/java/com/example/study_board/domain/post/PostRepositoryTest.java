package com.example.study_board.domain.post;

import com.example.study_board.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    private Post createPost(String title, String content, String author) {
        return Post.builder()
                .title(title)
                .content(content)
                .author(author)
                .build();
    }

    @Test
    @DisplayName("게시글 저장")
    void save_post() {
        Post post = createPost("제목", "내용", "작성자");

        Post saved = postRepository.save(post);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("내용");
        assertThat(saved.getAuthor()).isEqualTo("작성자");
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("게시글 단건 조회")
    void findById_post() {
        Post saved = postRepository.save(createPost("제목", "내용", "작성자"));

        Optional<Post> found = postRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("제목");
        assertThat(found.get().getContent()).isEqualTo("내용");
        assertThat(found.get().getAuthor()).isEqualTo("작성자");
    }

    @Test
    @DisplayName("게시글 목록 조회")
    void findAll_posts() {
        postRepository.save(createPost("제목1", "내용1", "작성자1"));
        postRepository.save(createPost("제목2", "내용2", "작성자2"));

        List<Post> posts = postRepository.findAll();

        assertThat(posts).hasSize(2);
    }

    @Test
    @DisplayName("게시글 삭제")
    void delete_post() {
        Post saved = postRepository.save(createPost("제목", "내용", "작성자"));

        postRepository.delete(saved);

        Optional<Post> found = postRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("제목으로 키워드 검색")
    void search_by_keyword_in_title() {
        postRepository.save(createPost("Spring Boot 입문", "내용1", "작성자"));
        postRepository.save(createPost("JPA 학습", "내용2", "작성자"));
        postRepository.save(createPost("Spring Security", "내용3", "작성자"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = postRepository.searchByKeyword("Spring", pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(post -> post.getTitle().contains("Spring"));
    }

    @Test
    @DisplayName("내용으로 키워드 검색")
    void search_by_keyword_in_content() {
        postRepository.save(createPost("제목1", "Spring Boot는 쉽다", "작성자"));
        postRepository.save(createPost("제목2", "JPA는 어렵다", "작성자"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = postRepository.searchByKeyword("Spring", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).contains("Spring");
    }

    @Test
    @DisplayName("키워드 검색 결과 없음")
    void search_returns_empty_when_no_match() {
        postRepository.save(createPost("제목", "내용", "작성자"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = postRepository.searchByKeyword("없는키워드", pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("페이징 동작 확인")
    void findAll_with_pageable() {
        for (int i = 1; i <= 15; i++) {
            postRepository.save(createPost("제목" + i, "내용" + i, "작성자"));
        }

        PageRequest pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> firstPage = postRepository.findAll(pageable);

        assertThat(firstPage.getContent()).hasSize(5);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
    }
}
