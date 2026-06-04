package com.example.study_board.domain.post;

import com.example.study_board.dto.post.PostListResponse;
import com.example.study_board.dto.post.PostSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * QueryDSL 기반 동적 검색(Phase 14). Spring Data 네이밍 규칙상 구현체는 {@code PostRepositoryImpl}이어야 한다.
 */
public interface PostRepositoryCustom {

    Page<PostListResponse> search(PostSearchCondition condition, Pageable pageable);
}
