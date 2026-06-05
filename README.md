# study_board — Spring Boot 게시판

> **TDD(Red-Green-Refactor)로 18개 Phase에 걸쳐 점진적으로 쌓아 올린 Spring Boot 학습 프로젝트.**
> 신입~주니어 백엔드 면접 최빈출 주제를 **게시판이라는 하나의 도메인** 위에서 한 겹씩 구현하며 익힌다. REST API 전용(서버 사이드 렌더링 없음).

각 Phase는 "기능 추가"가 아니라 **새로운 백엔드 개념의 학습**을 목표로 하며, 구현 중 밟은 함정과 트레이드오프가 코드 주석과 [PLAN.md](./PLAN.md)에 상세히 기록돼 있다.

---

## 핵심 특징

- **인증/인가** — Spring Security 필터체인 + JWT(access/refresh) + BCrypt, 소유권 기반 인가(작성자/ADMIN만 수정·삭제)
- **JPA 심화** — N+1 진단(Hibernate Statistics)과 해결(`@EntityGraph` / COUNT 서브쿼리 DTO projection), OSIV off 설계
- **QueryDSL 동적 검색** — 키워드·작성자·카테고리·태그 조건부 쿼리 + 페이징
- **실 DB 운영** — MySQL 8.4 + Flyway 마이그레이션(`ddl-auto=validate`) + docker-compose
- **동시성 / 캐싱** — 조회수 lost update를 원자적 UPDATE로 제거, 인기글 Redis 캐싱(TTL + 무효화 전략)
- **계층형 댓글** — 자기참조(인접 리스트) 무제한 depth 트리 + 2축 삭제(tombstone)
- **이벤트 기반 알림** — 도메인 이벤트 + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + SSE 실시간 푸시
- **테스트 / 자동화** — 슬라이스·단위·통합·동시성 테스트, Testcontainers 실 MySQL 검증, GitHub Actions CI

---

## 기술 스택

| 분류 | 사용 기술 |
|------|-----------|
| Language / Runtime | Java 17 |
| Framework | Spring Boot 4.0.6 (Spring 7, Jackson 3 / `tools.jackson`) |
| Persistence | Spring Data JPA (Hibernate 7.2), QueryDSL 7.2 (OpenFeign fork) |
| Database | MySQL 8.4 (dev·prod) / H2 in-memory (test) |
| Migration | Flyway (`V1`~`V5`) |
| Security | Spring Security 7, JWT (jjwt 0.12.6), BCrypt |
| Cache | Redis 7 (`spring-boot-starter-cache` + data-redis) |
| Docs | springdoc-openapi 3.0.3 (Swagger UI) |
| Test | JUnit 5, Mockito, Spring Test slices, Testcontainers, Awaitility |
| Build / CI | Gradle, GitHub Actions |

> ⚠️ **Spring Boot 4 호환 주의** — QueryDSL은 OpenFeign 포크 `7.2`, springdoc은 `3.0.x`, jjwt-jackson은 내부 격리, Testcontainers는 BOM 관리(버전 고정 금지)여야 한다. 각 함정은 [PLAN.md](./PLAN.md)와 `build.gradle` 주석에 정리돼 있다.

---

## 아키텍처

```
src/main/java/com/example/study_board/
├── domain/            도메인별 Entity · Repository · Service · Controller
│   ├── post/  comment/  member/  auth/
│   ├── category/  tag/  like/  notification/
├── dto/               요청/응답 DTO (전부 Java record)
│   └── post/  comment/  member/  auth/  notification/
├── global/
│   ├── config/        SecurityConfig, JpaAuditingConfig, QueryDslConfig, CacheConfig, AsyncConfig, OpenApiConfig
│   ├── exception/     GlobalExceptionHandler, ErrorCode, BusinessException 계층
│   └── security/      JwtProvider, JwtAuthenticationFilter, CustomUserDetails(Service)
└── common/            BaseTimeEntity (createdAt/updatedAt/createdBy/lastModifiedBy)
```

**레이어 규칙** — Controller(요청/응답·인증 principal 추출) → Service(`@Transactional` 비즈니스 로직·DTO 완성) → Repository(JPA/QueryDSL). 엔티티는 API로 노출하지 않고 DTO `record`로 변환한다. OSIV가 꺼져 있어 LAZY 로딩은 **서비스 트랜잭션 안에서** 끝낸다.

---

## 빠른 시작

```bash
# 1. 인프라 기동 (MySQL 8.4 + Redis 7)
docker-compose up -d

# 2. 애플리케이션 실행 (기본 프로필: dev)
./gradlew bootRun

# 3. API 문서 확인
#    Swagger UI : http://localhost:8080/swagger-ui.html
#    OpenAPI    : http://localhost:8080/v3/api-docs
```

**프로필**
- `dev` (기본) — 로컬 MySQL + Flyway + `validate`, `show-sql=true`, Swagger 활성
- `prod` — DB·JWT 시크릿 등 모든 민감값 환경변수 주입, Swagger 비활성
- `test` — H2 in-memory + Flyway off + `create-drop` (통합/동시성 테스트만 Testcontainers 실 MySQL)

---

## 주요 API

| Method | URL | 설명 | 인증 |
|--------|-----|------|:----:|
| POST | `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh` | 회원가입 / 로그인 / 토큰 재발급 | 공개 |
| GET | `/api/posts?keyword=&author=&categoryId=&tag=&page=&size=` | 게시글 동적 검색 + 페이징 | 공개 |
| GET | `/api/posts/{id}`, `/api/posts/popular` | 단건 조회 / 인기글(캐시) | 공개 |
| POST · PUT · DELETE | `/api/posts`, `/api/posts/{id}` | 작성 / 수정 / 삭제(soft) | 필요 |
| POST · GET | `/api/posts/{postId}/comments` | 댓글·대댓글 작성 / 트리 조회 | 작성만 필요 |
| PUT · DELETE | `/api/comments/{id}` | 댓글 수정 / 삭제 | 필요 |
| POST · DELETE | `/api/posts/{postId}/likes` | 좋아요 / 취소 | 필요 |
| GET · PATCH | `/api/notifications`, `/api/notifications/{id}/read`, `/api/notifications/subscribe`(SSE) | 알림 목록 / 읽음 / 실시간 구독 | 필요 |

> 쓰기 요청은 `Authorization: Bearer <accessToken>` 헤더가 필요하다. 타인 리소스 수정·삭제는 403, 없는 리소스는 404가 우선한다.

---

## Phase별 학습 맵

전체 여정은 **기초(0~9) → 프로덕션화(10~16) → 기능 확장(17~18)** 세 묶음으로 진행된다. 각 Phase의 상세 구현·검증·함정은 [PLAN.md](./PLAN.md)에 있다.

### 기초 다지기 (0~9)

| Phase | 주제 | 배우는 핵심 개념 |
|:-----:|------|------------------|
| 0 | 프로젝트 설정 | 외부 설정, `ddl-auto` 모드 |
| 1 | Post 엔티티 + Repository | JPA 엔티티 매핑, JPA Auditing, `@DataJpaTest` |
| 2 | Service + Controller + DTO | 계층형 아키텍처, 생성자 주입, DTO 패턴, dirty checking |
| 3 | 예외 처리 | `@RestControllerAdvice`, 표준 에러 응답 포맷 |
| 4 | Validation | Bean Validation, `@NotBlank` vs `@NotNull` vs `@NotEmpty` |
| 5 | Comment + 연관관계 | `@ManyToOne(LAZY)` / `@OneToMany`, cascade, 쿼리 메서드 |
| 6 | 페이징 + 검색 | `Pageable`, `Page<T>`, `@Query`(JPQL) |
| 7 | 테스트 코드 | 슬라이스/단위/통합, 테스트 피라미드 |
| **8** | **JPA 심화** | **N+1 진단·해결**: fetch join vs `@EntityGraph`, 컬렉션 fetch + Pageable 함정, 양방향 편의 메서드 |
| 9 | 실무 인프라 | Spring Profile, slf4j 로깅 레벨, **OSIV 트레이드오프** |

### 프로덕션화 (10~16)

| Phase | 주제 | 배우는 핵심 개념 · 대표 함정 |
|:-----:|------|------------------------------|
| 10 | 에러코드 체계 | `ErrorCode` enum + `BusinessException` 계층으로 에러 계약 통일 |
| **11** | **Security + JWT + Member** ⭐ | 필터체인 순서, `SecurityContextHolder`, 세션 vs JWT(stateless), BCrypt, 인증 vs 인가 |
| 12 | 소유권 인가 | 도메인 권한 검증 vs `@PreAuthorize`, **403 vs 404 정책**(존재 노출 회피) |
| 13 | MySQL + Flyway + 감사 + soft delete | `validate`가 정석인 이유, `@SQLRestriction`/`@SQLDelete`, `AuditorAware`, utf8mb4·`DATETIME(6)` |
| 14 | QueryDSL + 카테고리/태그/좋아요 | 타입세이프 동적 쿼리, nullable 연관 **LEFT JOIN** 누락, COUNT 서브쿼리 projection으로 N+1 회피 |
| 15 | Swagger / OpenAPI | JWT `SecurityScheme` 문서화, prod 비활성화 |
| 16 | CI + Testcontainers + 동시성/캐시 | **lost update → 원자적 UPDATE**, Redis 캐시 무효화(`beforeInvocation`), Testcontainers가 H2보다 신뢰성 높은 이유 |

### 기능 확장 (17~18)

| Phase | 주제 | 배우는 핵심 개념 · 대표 함정 |
|:-----:|------|------------------------------|
| **17** | **대댓글 (계층형)** | 자기참조(인접 리스트), 메모리 트리 조립으로 계층 N+1 회피, **2축 삭제**(`deleted_at` 숨김 vs `deleted` tombstone 노출), `boolean`→`BIT` validate 함정 |
| **18** | **알림 (이벤트 기반)** | 도메인 이벤트 decoupling, **`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`** 트랜잭션 경계, 별도 스레드의 영속성/SecurityContext 분리, SSE 실시간 푸시 |

⭐ = 면접 최빈출 핵심 Phase

---

## 면접 토킹 포인트

이 프로젝트로 설명할 수 있는 대표 질문들:

- **N+1을 어떻게 발견하고 해결했나?** → Hibernate Statistics로 쿼리 수를 테스트에 단언(Phase 8/14). 목록은 COUNT 서브쿼리 DTO projection으로, 단건은 `@EntityGraph`로 해결. 컬렉션 fetch join + Pageable의 메모리 페이징 함정까지 비교.
- **OSIV를 왜 껐나?** → LAZY 로딩을 서비스 트랜잭션 경계 안에서 완성하는 설계를 강제하기 위해(Phase 9). 통합 테스트에 `@Transactional`을 붙이면 이 안전성이 가려지는 이유까지.
- **인증과 인가의 차이를 코드로?** → JWT 필터로 인증(Phase 11), 서비스 레이어 `verifyOwnership`로 인가(Phase 12). 403 vs 404 정책.
- **동시성 제어?** → 조회수 lost update를 `@Modifying` 원자적 UPDATE로 제거하고 50스레드 테스트로 검증(Phase 16).
- **이벤트 기반 설계의 이점은?** → 댓글 로직과 알림 로직을 도메인 이벤트로 분리. SSE 실시간 채널을 **기존 코드 수정 없이** 같은 파이프라인에 추가(Phase 18).

---

## 테스트

```bash
./gradlew test                                              # 전체
./gradlew test --tests "com.example.study_board.domain.post.PostServiceTest"   # 단일 클래스
```

**전략** — 레이어별 슬라이스 테스트(`@DataJpaTest` / `@ExtendWith(MockitoExtension)` / `@WebMvcTest`)를 기본으로, 마이그레이션·방언 정합성이 중요한 통합/동시성/캐시 테스트는 **Testcontainers 실 MySQL·Redis**에서 실행한다. 비동기 알림은 비트랜잭션 + Awaitility로 검증.

**컨벤션** — `@DisplayName` 한글, 메서드명 영문 snake_case, AssertJ `assertThat()`만 사용, Given/When/Then 구조. 자세한 규칙은 [CLAUDE.md](./CLAUDE.md) 참조.

---

## 추가 문서

- **[PLAN.md](./PLAN.md)** — 18개 Phase의 전체 로드맵, 단계별 구현/검증 내역, 밟은 함정과 트레이드오프 학습 노트
- **[CLAUDE.md](./CLAUDE.md)** — TDD 규칙, 레이어별 테스트 어노테이션, 코드/테스트 컨벤션
