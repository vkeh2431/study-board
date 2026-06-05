package com.example.study_board.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정(Phase 18). {@code @EnableAsync}로 {@code @Async} 프록시를 켜고, 알림 전용 스레드 풀을 등록한다.
 *
 * <p><b>왜 커스텀 Executor인가</b>: 기본 {@code SimpleAsyncTaskExecutor}는 작업마다 스레드를 새로 만들어(풀링 없음)
 * 부하가 몰리면 위험하다. 코어/맥스 풀·큐 용량·거부 정책·스레드명 prefix를 직접 정해 자원을 통제한다.
 * <ul>
 *   <li>core 2 / max 5 / queue 100 — 알림은 가볍고 빈도가 낮아 작게 잡는다(큐가 버퍼).</li>
 *   <li>{@code notify-} prefix — 로그에서 비동기 스레드를 식별(트레이싱).</li>
 *   <li>{@code CallerRunsPolicy} — 풀+큐 포화 시 호출 스레드가 직접 실행해 작업 유실을 막는다(백프레셔).</li>
 * </ul>
 *
 * <p>⚠️ {@code @Async} 스레드는 부모의 {@code SecurityContext}를 상속하지 않는다 → {@code AuditorAware}가 비어
 * 알림의 {@code createdBy}는 NULL이 된다(전파하려면 {@code DelegatingSecurityContextExecutor}). 학습 노트.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
