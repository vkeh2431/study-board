package com.example.study_board.global.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * 캐시 설정(Phase 16). {@code @EnableCaching}으로 {@code @Cacheable}/{@code @CacheEvict}를 활성화한다.
 *
 * <p>인기글 캐시({@code popularPosts})에 TTL 5분을 둔다. {@link RedisCacheManagerBuilderCustomizer}는
 * Spring Boot가 RedisCacheManager를 만들 때만 적용되므로, 테스트의 {@code spring.cache.type=simple}
 * (ConcurrentMapCacheManager)에는 영향을 주지 않는다 → 캐시를 쓰지 않는 통합 테스트는 Redis 없이도 동작한다.
 *
 * <p>⚠️ Boot 4 패키지: {@code RedisCacheManagerBuilderCustomizer}는 모듈 분리로
 * {@code org.springframework.boot.cache.autoconfigure}에 있다(구 {@code ...autoconfigure.cache} 아님).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer popularPostsCacheCustomizer() {
        return builder -> builder.withCacheConfiguration("popularPosts",
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues());
    }
}
