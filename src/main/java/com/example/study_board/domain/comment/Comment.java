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
import java.util.ArrayList;
import java.util.List;

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

    // 대댓글 자기참조 (Phase 17, 인접 리스트 모델). 루트 댓글은 parent=null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /**
     * 자기참조 양방향의 역방향(학습용). 조회 경로에서 절대 navigate 하지 않는다 —
     * 계층 조회는 한 게시글의 평면 리스트를 1쿼리로 가져와 메모리에서 트리로 조립한다(N+1 회피, Phase 8 연계).
     */
    @OneToMany(mappedBy = "parent")
    private List<Comment> children = new ArrayList<>();

    /**
     * tombstone 플래그(Phase 17). 자식이 있는 댓글을 삭제하면 트리 유지를 위해 행은 보이게 둔 채
     * 본문만 "삭제된 댓글입니다"로 가린다. {@code deletedAt}(@SQLRestriction이 조회에서 숨기는
     * 하드 soft delete)과 구분되는 별개의 축이다: tombstone은 {@code deleted_at=NULL}이라 조회에 계속 노출된다.
     */
    @Column(nullable = false)
    private boolean deleted;

    private LocalDateTime deletedAt;

    @Builder
    public Comment(String content, Member member, Post post, Comment parent) {
        this.content = content;
        this.member = member;
        this.post = post;
        this.parent = parent;
    }

    public void update(String content) {
        this.content = content;
    }

    /**
     * 부모 댓글 식별자(없으면 null). LAZY {@code parent} 프록시의 식별자는 FK에서 읽히므로
     * 추가 SELECT 없이 트리 조립에 사용한다({@link #isOwner(Long)}와 같은 기법).
     */
    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    /**
     * tombstone 처리(자식 있는 댓글 삭제). {@code repository.delete()}(=@SQLDelete, deleted_at 설정)와 달리
     * 행을 조회에 남겨둔 채 {@code deleted=true}로만 표시한다(dirty checking). 본문 마스킹은 응답 DTO에서 한다(비파괴).
     */
    public void markDeleted() {
        this.deleted = true;
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
