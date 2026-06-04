package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * QueryDSL 기반 동적 검색(Phase 14). Spring Data 네이밍 규칙상 구현체는 {@code PostRepositoryImpl}이어야 한다.
 */
public interface PostRepositoryCustom {

    Page<PostListResponse> search(PostSearchCondition condition, Pageable pageable);

    /** 조회수 상위 {@code limit}개 인기글(Phase 16: Redis 캐싱 대상). search와 같은 DTO projection을 재사용한다. */
    List<PostListResponse> findPopular(int limit);
}
