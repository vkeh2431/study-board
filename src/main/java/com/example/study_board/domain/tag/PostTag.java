package com.example.study_board.domain.tag;

import com.example.study_board.common.BaseTimeEntity;
import com.example.study_board.domain.post.Post;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Post ↔ Tag 다대다를 풀어쓴 중간 엔티티(Phase 14).
 * 한 게시글에 같은 태그가 중복 부착되지 않도록 (post_id, tag_id) 유니크 제약을 둔다.
 * soft delete 대상이 아니며, Post가 soft delete돼도 그대로 남는다(Post가 @SQLRestriction으로 안 보임).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_post_tag", columnNames = {"post_id", "tag_id"}))
public class PostTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Builder
    public PostTag(Post post, Tag tag) {
        this.post = post;
        this.tag = tag;
    }
}
