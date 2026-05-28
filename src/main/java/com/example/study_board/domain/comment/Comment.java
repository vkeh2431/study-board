package com.example.study_board.domain.comment;

import com.example.study_board.common.BaseTimeEntity;
import com.example.study_board.domain.post.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Builder
    public Comment(String content, String author, Post post) {
        this.content = content;
        this.author = author;
        this.post = post;
    }

    public void update(String content) {
        this.content = content;
    }

    /**
     * 양방향 동기화 전용. 외부에서는 {@link Post#addComment(Comment)}를 호출해야 한다.
     * Post와 Comment가 다른 패키지에 있어 가시성을 public으로 두지만,
     * 직접 호출은 Post.comments 컬렉션과의 동기화를 깨뜨릴 수 있다.
     */
    public void assignPost(Post post) {
        this.post = post;
    }
}
