package com.example.study_board.domain.comment;

import com.example.study_board.common.BaseTimeEntity;
import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.post.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// soft delete (Phase 13): Post 삭제 시 cascade=REMOVE가 각 댓글의 @SQLDelete를 호출해 함께 soft delete된다.
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE comment SET deleted_at = NOW(6) WHERE id = ?")
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    private LocalDateTime deletedAt;

    @Builder
    public Comment(String content, Member member, Post post) {
        this.content = content;
        this.member = member;
        this.post = post;
    }

    public void update(String content) {
        this.content = content;
    }

    /**
     * 주어진 회원이 이 댓글의 작성자인지 판별한다(소유권 인가, Phase 12).
     * LAZY {@code member} 프록시의 식별자는 FK에서 읽히므로 추가 SELECT 없이 비교한다.
     */
    public boolean isOwner(Long memberId) {
        return memberId != null && member != null
                && member.getId() != null && member.getId().equals(memberId);
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
