package com.example.study_board.domain.post;

import com.example.study_board.domain.category.QCategory;
import com.example.study_board.domain.comment.QComment;
import com.example.study_board.domain.like.QPostLike;
import com.example.study_board.domain.member.QMember;
import com.example.study_board.domain.tag.QPostTag;
import com.example.study_board.domain.tag.QTag;
import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 게시글 동적 검색(Phase 14). 목록은 엔티티가 아니라 {@link PostListResponse} DTO projection으로 조회한다.
 * 댓글 수·좋아요 수는 SELECT 절 상관 COUNT 서브쿼리로 인라인하므로 컬렉션을 로딩하지 않는다 →
 * "컬렉션 fetch join + Pageable 메모리 페이징" 함정을 피하고 N+1도 발생하지 않는다(Phase 8 학습노트 실구현).
 * 서브쿼리가 2개여도 메인 SQL에 인라인되어 PreparedStatement는 content 1 + count 1 = 2건으로 고정이다.
 * Comment의 {@code @SQLRestriction("deleted_at IS NULL")}은 이 서브쿼리 SQL에도 부착되어
 * soft-deleted 댓글은 카운트에서 자동 제외된다(PostLike는 soft delete 대상 아님).
 */
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    /** 정렬 허용 컬럼 화이트리스트. 임의 경로(예: member.password) 정렬 주입을 막는다. */
    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "viewCount");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<PostListResponse> search(PostSearchCondition condition, Pageable pageable) {
        QPost post = QPost.post;
        QComment comment = QComment.comment;
        QPostLike postLike = QPostLike.postLike;
        QMember member = QMember.member;
        QCategory category = QCategory.category;
        QPostTag postTag = QPostTag.postTag;
        QTag tag = QTag.tag;

        boolean filterByTag = condition.tag() != null && !condition.tag().isBlank();

        JPAQuery<PostListResponse> contentQuery = queryFactory
                .select(Projections.constructor(PostListResponse.class,
                        post.id,
                        post.title,
                        member.username,
                        category.name,
                        post.viewCount,
                        JPAExpressions.select(comment.count())
                                .from(comment)
                                .where(comment.post.eq(post)),
                        JPAExpressions.select(postLike.count())
                                .from(postLike)
                                .where(postLike.post.eq(post)),
                        post.createdAt))
                .from(post)
                .join(post.member, member)
                .leftJoin(post.category, category); // nullable → LEFT (무카테고리 글 누락 방지)

        JPAQuery<Long> countQuery = filterByTag
                ? queryFactory.select(post.countDistinct()).from(post)
                : queryFactory.select(post.count()).from(post);

        if (filterByTag) {
            // 태그 조건이 있을 때만 join (항상 join하면 태그 없는 글이 누락). 한 글이 여러 태그를 가질 수 있어 distinct.
            contentQuery.join(post.postTags, postTag).join(postTag.tag, tag)
                    .where(tag.name.eq(condition.tag())).distinct();
            countQuery.join(post.postTags, postTag).join(postTag.tag, tag)
                    .where(tag.name.eq(condition.tag()));
        }

        BooleanExpression[] predicates = {
                keywordContains(condition.keyword()),
                authorContains(condition.author()),
                categoryIdEq(condition.categoryId())
        };

        List<PostListResponse> content = contentQuery
                .where(predicates)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        countQuery.where(predicates);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null; // null이면 where에서 무시된다
        }
        return QPost.post.title.contains(keyword).or(QPost.post.content.contains(keyword));
    }

    private BooleanExpression authorContains(String author) {
        if (author == null || author.isBlank()) {
            return null;
        }
        return QPost.post.member.username.contains(author);
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return (categoryId == null) ? null : QPost.post.category.id.eq(categoryId);
    }

    /**
     * Pageable의 Sort를 QueryDSL OrderSpecifier로 변환한다.
     * 화이트리스트에 없는 정렬 키는 무시하고, 결과가 비면 createdAt DESC를 기본으로 둔다.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        PathBuilder<Post> path = new PathBuilder<>(Post.class, "post");
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT.contains(order.getProperty())) {
                continue;
            }
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            orders.add(new OrderSpecifier(direction, path.get(order.getProperty())));
        }
        if (orders.isEmpty()) {
            orders.add(new OrderSpecifier(Order.DESC, path.get("createdAt")));
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
