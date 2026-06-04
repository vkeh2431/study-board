package com.example.study_board.domain.post;

import com.example.study_board.common.BaseTimeEntity;
import com.example.study_board.domain.comment.Comment;
import com.example.study_board.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// soft delete (Phase 13): delete()는 물리 삭제 대신 deleted_at UPDATE로 치환되고(@SQLDelete),
// 모든 조회에 deleted_at IS NULL 조건이 자동 부착된다(@SQLRestriction).
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE post SET deleted_at = NOW(6) WHERE id = ?")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private int viewCount;

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE)
    private List<Comment> comments = new ArrayList<>();

    private LocalDateTime deletedAt;

    @Builder
    public Post(String title, String content, Member member) {
        this.title = title;
        this.content = content;
        this.member = member;
        this.viewCount = 0;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    /**
     * 주어진 회원이 이 게시글의 작성자인지 판별한다(소유권 인가, Phase 12).
     * LAZY {@code member} 프록시의 식별자는 FK에서 읽히므로 추가 SELECT 없이 비교한다.
     * ({@code getUsername()} 등 다른 필드 접근은 프록시를 초기화하므로 호출하지 않는다.)
     */
    public boolean isOwner(Long memberId) {
        return memberId != null && member != null
                && member.getId() != null && member.getId().equals(memberId);
    }

    public void addComment(Comment comment) {
        this.comments.add(comment);
        comment.assignPost(this);
    }
}
