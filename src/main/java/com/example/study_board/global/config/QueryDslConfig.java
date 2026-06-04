package com.example.study_board.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 동적 쿼리용 {@link JPAQueryFactory} 빈 등록(Phase 14).
 * 트랜잭션에 바인딩된 {@code EntityManager} 프록시가 주입되므로 readOnly 트랜잭션 안에서 안전하다.
 * {@code @DataJpaTest} 슬라이스에서는 {@code @Import(QueryDslConfig.class)}로 가져와 쓴다.
 */
@Configuration
public class QueryDslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
