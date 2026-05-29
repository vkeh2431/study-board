# Spring Boot 게시판 (study_board) 구현 계획

## Context
Spring Boot 신입 개발자 취업 준비를 위한 학습 프로젝트. FastAPI/PHP 백엔드 1년 경험을 바탕으로, Spring Boot의 핵심 개념과 면접 빈출 주제를 게시판 구현을 통해 익힌다. REST API 전용(Thymeleaf 없음), H2 인메모리 DB 사용.

---

## DB 스키마

**post** 테이블: `id(PK)`, `title`, `content`, `author`, `view_count`, `created_at`, `updated_at`
**comment** 테이블: `id(PK)`, `post_id(FK)`, `content`, `author`, `created_at`, `updated_at`
- Post 1 : N Comment (게시글 삭제 시 댓글 cascade 삭제)

> 🔜 **Phase 11/13 예정 변경**: `member` 테이블(`id`, `email`, `username`, `password`, `role`) 신설. `post.author`/`comment.author` 문자열을 `post.member_id(FK)`/`comment.member_id(FK)`로 전환(작성자는 인증 컨텍스트에서 주입). Phase 13에서 `created_by`/`modified_by` 감사 컬럼 + `deleted_at`(soft delete) 추가.

---

## 패키지 구조

```
com.example.study_board
├── domain/
│   ├── post/        (Post, PostRepository, PostService, PostController)
│   └── comment/     (Comment, CommentRepository, CommentService, CommentController)
├── dto/
│   ├── post/        (PostCreateRequest, PostUpdateRequest, PostResponse, PostListResponse)
│   └── comment/     (CommentCreateRequest, CommentUpdateRequest, CommentResponse)
├── global/
│   ├── exception/   (GlobalExceptionHandler, ErrorResponse, ResourceNotFoundException)
│   └── config/      (JpaAuditingConfig)
└── common/
    └── BaseTimeEntity.java (@MappedSuperclass - createdAt/updatedAt)
```

---

## API 엔드포인트

| Method | URL | 설명 | Status |
|--------|-----|------|--------|
| POST | `/api/posts` | 게시글 작성 | 201 |
| GET | `/api/posts/{id}` | 게시글 단건 조회 | 200 |
| GET | `/api/posts?page=&size=&keyword=` | 게시글 목록 (페이징/검색) | 200 |
| PUT | `/api/posts/{id}` | 게시글 수정 | 200 |
| DELETE | `/api/posts/{id}` | 게시글 삭제 | 204 |
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 201 |
| GET | `/api/posts/{postId}/comments` | 댓글 목록 조회 | 200 |
| PUT | `/api/comments/{id}` | 댓글 수정 | 200 |
| DELETE | `/api/comments/{id}` | 댓글 삭제 | 204 |

---

## 구현 순서 (8단계)

### Phase 0: 프로젝트 설정
- [x] `application.properties`에 H2, JPA, 로깅 설정
- **배우는 것**: 외부 설정, ddl-auto 모드, H2 콘솔
- **검증**: `./gradlew bootRun` 후 `localhost:8080/h2-console` 접속 확인

### Phase 1: Post 엔티티 + Repository
- [x] `BaseTimeEntity` (@MappedSuperclass, @CreatedDate, @LastModifiedDate)
- [x] `JpaAuditingConfig` (@EnableJpaAuditing)
- [x] `Post` 엔티티 (@Entity, @Id, @GeneratedValue, Lombok @Getter/@Builder/@NoArgsConstructor(PROTECTED))
- [x] `PostRepository` (JpaRepository 상속)
- [x] `PostRepositoryTest` 작성 및 실행
- **배우는 것**: JPA 엔티티 매핑, JPA Auditing, Repository 추상화, @DataJpaTest
- **검증**: PostRepositoryTest 작성 및 실행, H2 콘솔에서 테이블 확인

### Phase 2: Post Service + Controller + DTO
- [x] Request/Response DTO (Java `record`로 구현)
- [x] `PostService` (@Service, @Transactional)
- [x] `PostController` (@RestController, @RequestMapping)
- **배우는 것**: 계층형 아키텍처, 생성자 주입, DTO 패턴, dirty checking, ResponseEntity
- **검증**: curl로 CRUD 전체 테스트

### Phase 3: 예외 처리
- [x] `ResourceNotFoundException` (커스텀 예외)
- [x] `ErrorResponse` (표준 에러 응답 DTO)
- [x] `GlobalExceptionHandler` (@RestControllerAdvice)
- [x] PostService에서 커스텀 예외 적용
- **배우는 것**: @RestControllerAdvice, @ExceptionHandler, 표준 에러 응답 포맷
- **검증**: 존재하지 않는 ID 조회 시 404 JSON 응답 확인

### Phase 4: Validation
- [x] `build.gradle`에 `spring-boot-starter-validation` 의존성 추가
- [x] Request DTO에 @NotBlank, @Size 등 검증 어노테이션 추가
- [x] Controller에 @Valid 적용
- **배우는 것**: Bean Validation, @NotBlank vs @NotNull vs @NotEmpty 차이 (면접 빈출)
- **검증**: 빈 값으로 POST 요청 시 400 에러 + 필드별 에러 메시지 확인

### Phase 5: Comment 엔티티 + CRUD
- [x] `Comment` 엔티티 (@ManyToOne(fetch=LAZY), @JoinColumn)
- [x] `Post`에 @OneToMany(mappedBy, cascade=REMOVE) 추가
- [x] `CommentRepository` (findByPostIdOrderByCreatedAtDesc 쿼리 메서드)
- [x] CommentService, CommentController, DTO 구현
- **배우는 것**: JPA 연관관계 (면접 최빈출), LAZY vs EAGER, cascade, 쿼리 메서드 네이밍
- **검증**: 댓글 CRUD + 게시글 삭제 시 댓글 cascade 삭제 확인

### Phase 6: 페이징 + 검색
- [x] `PostRepository`에 `@Query`로 제목+내용 검색 추가
- [x] PostService/Controller에 keyword 파라미터 연동
- **배우는 것**: Pageable, Page<T>, @Query(JPQL), @Param, @PageableDefault
- **검증**: 15개 이상 게시글 생성 후 페이징/검색 테스트

### Phase 7: 테스트 코드
- [x] `PostRepositoryTest` (@DataJpaTest)
- [x] `PostServiceTest` (Mockito - @Mock, @InjectMocks)
- [x] `PostControllerTest` (@WebMvcTest + MockMvc)
- [x] `CommentServiceTest` (Mockito)
- [x] `PostIntegrationTest` (@SpringBootTest + @AutoConfigureMockMvc)
- **배우는 것**: 슬라이스 테스트, MockMvc, Mockito, 테스트 피라미드
- **검증**: `./gradlew test` 전체 통과

---

## 추가 보완 (신입 면접 빈출 갭 메우기)

Phase 0~7 완료 후 코드 분석 결과, 면접 최빈출 주제 중 다음이 빠져 있어 Phase 8/9로 보완.

### 사전 점검: `tools.jackson` import 확인
`PostControllerTest`, `CommentControllerTest`, `PostIntegrationTest`가 `tools.jackson.databind.ObjectMapper`를 import 중.
Spring Boot 4.x 정식 패키지인지 확인:
```bash
./gradlew test --tests "*PostControllerTest" -i
```
- 통과하면 그대로 둔다.
- 컴파일 실패하면 `com.fasterxml.jackson.databind.ObjectMapper`로 교체.

### Phase 8: JPA 심화 (N+1, Fetch Join, 양방향 편의 메서드)
- [x] `PostListResponse`에 `commentCount` 필드 추가 → `PostService.findAll`에서 `post.getComments().size()`로 N+1 발생 경로 생성
- [x] `application-test.properties`에 `spring.jpa.properties.hibernate.generate_statistics=true` + `default_batch_fetch_size=-1` 추가
- [x] **Red**: `PostQueryPerformanceTest` 신설. Hibernate Statistics로 쿼리 수 단언 → 7개(count+findAll+5×comments) 발생 확인
- [x] **Green**: `PostRepository.findAll` override + `searchByKeyword`에 `@EntityGraph(attributePaths = {"comments"})` 적용 → 2개(count+JOIN FETCH)로 감소
- [x] `Post`에 `addComment(Comment)` 편의 메서드 추가, `Comment.assignPost(Post)`는 javadoc 경고와 함께 public 노출
- [x] `CommentService.create`에서 `post.addComment(comment)` 호출하도록 리팩터링
- **배우는 것**: LAZY 동작 원리, N+1 발생 조건, fetch join vs @EntityGraph 차이, 컬렉션 fetch join + Pageable 함정, mappedBy 동기화 책임
- **검증**: ✅ 전체 테스트 GREEN

#### 학습 노트: N+1 해결 전략 트레이드오프

| 방식 | 장점 | 단점 |
|---|---|---|
| `@EntityGraph` (적용 방식) | 코드 변경 최소, distinct 자동 처리 (Hibernate 6+) | 컬렉션 fetch + Pageable 시 메모리 페이징 위험 |
| `JPQL JOIN FETCH` + `DISTINCT` | 명시적, 학습 효과 | 동일하게 메모리 페이징 위험, distinct 직접 명시 필요 |
| COUNT 서브쿼리 DTO projection | 페이징 안전, 컬렉션 전체 로딩 안 함 | DTO 전용 쿼리 작성 필요 |
| `@BatchSize` / `default_batch_fetch_size` | 자동 IN-clause로 N+1 완화 | 여전히 +1 쿼리, 메인 properties에 살아있어 테스트에서 의도가 가려질 수 있음 |

> ⚠️ **컬렉션 fetch join + Pageable**: Hibernate가 전체 결과를 메모리에 로드 후 자바에서 페이징. 데이터가 많아지면 OOM. 프로덕션에서는 ① ID 두 단계 쿼리(IDs 페이징 → 본문 fetch) ② COUNT 서브쿼리 DTO projection ③ `@BatchSize`로 회피.
>
> ⚠️ **테스트에서 `batch_fetch_size`를 -1로 비활성화한 이유**: 메인 `application.properties`의 `default_batch_fetch_size=10`이 살아있으면 IN-clause로 묶여 N+1이 `1 + ceil(N/10)`으로 가려져 학습 의도가 실패한다.

### Phase 9: 실무 인프라 (Profile, 로깅, OSIV)
- [ ] `application-dev.properties` 신설 (현재 H2 메모리/show-sql/h2-console 설정 이관)
- [ ] `application-prod.properties` 신설 (`ddl-auto=validate`, `show-sql=false` 등)
- [ ] `application.properties`는 공통 항목만 남기고 `spring.profiles.active=dev` 설정
- [ ] `src/test/resources/application-test.properties` 신설 (Phase 8의 `generate_statistics` 등)
- [ ] `PostService`, `CommentService`에 `@Slf4j` + `log.info` 추가 (생성/수정/삭제 이벤트)
- [ ] `GlobalExceptionHandler`의 `handleException`에 `log.error("unhandled exception", e)` 추가 (현재 메시지를 삼키고 있음)
- [ ] `application.properties`에 `spring.jpa.open-in-view=false` 명시
- [ ] (선택) `PostIntegrationTest`에 `@Transactional`로 자동 롤백 적용 — Phase 8 N+1 측정 테스트와 충돌 가능하니 신중히 적용 또는 클래스 분리
- **배우는 것**: Spring Profile, slf4j 로깅 레벨, OSIV(Open Session In View)와 트레이드오프, 통합 테스트 자동 롤백
- **검증**: 기존 테스트 전부 통과 + dev 프로필 부팅 + 잘못된 입력/없는 ID/cascade 삭제 curl 시나리오

---

## 프로덕션화 로드맵 (Phase 10~16)

Phase 0~9로 학습 기초가 정리된 뒤, "프로덕션 같은" 프로젝트로 발전시키기 위한 로드맵.
**설계 원칙**: ① 인증/Member를 먼저(이후 후보들의 전제) ② 에러 계약을 Security 앞에 정리 ③ 모든 Phase는 기존 TDD(Red-Green-Refactor) 규칙 유지. `author` 제거처럼 기존 테스트가 깨지는 변경은 "테스트를 먼저 Red로 수정 → 프로덕션 코드 변경" 순서로 진행한다.
**사용자 결정 반영**: 실 DB(PostgreSQL + Docker)로 졸업, 인증/인가(Spring Security)를 최우선 중점 주제로.

### Phase 10: 공통 에러코드 enum + 예외 체계 정리
Security를 얹기 전 응답/에러 계약을 안정화. 작지만 모든 후속 Phase가 이 위에 쌓인다.
- [ ] `ErrorCode` enum 도입 (code 문자열, `HttpStatus`, defaultMessage 보유) — 흩어진 `"RESOURCE_NOT_FOUND"`, `"VALIDATION_ERROR"` 문자열 통합
- [ ] `BusinessException` 베이스 예외(`ErrorCode` 보유) 도입, `ResourceNotFoundException`을 이 체계로 편입
- [ ] `GlobalExceptionHandler`를 `ErrorCode` 기반으로 리팩터
- [ ] **TDD**: Controller 테스트의 에러 케이스 기대 JSON을 새 enum 값 기준으로 먼저 수정(Red) → 핸들러/예외 리팩터(Green) + `ErrorCode` 매핑 단위 테스트
- **배우는 것**: 에러 응답 일관성, enum + `@RestControllerAdvice` 조합, 예외 계층 설계
- **검증**: 없는 ID 조회 / 검증 실패 시 통일된 `code` 필드 JSON 응답 + 전체 테스트 GREEN
- ⚠️ 성공 응답 `ApiResponse<T>` 전체 래핑은 `Page<T>` 직렬화 충돌·면접 호불호로 **보류**(에러 포맷 통일만)

### Phase 11: Spring Security + JWT + Member 도메인 [최우선 핵심]
가장 크고 중요한 Phase. 신입~주니어 면접 최빈출(인증/인가, 필터체인)이며 나머지 절반의 전제.
- [ ] `Member` 엔티티(email/username, password(BCrypt), `Role` enum) + `MemberRepository`
- [ ] 회원가입 / 로그인 API, JWT 발급(access + refresh 권장)
- [ ] `SecurityConfig`(`SecurityFilterChain` bean), `JwtAuthenticationFilter`, 커스텀 principal 또는 `UserDetailsService`
- [ ] **author 마이그레이션**: `Post.author(String)` → `Post.member(@ManyToOne(LAZY))`, `Comment` 동일. 작성자는 `@AuthenticationPrincipal`에서 주입, `PostCreateRequest`/`CommentCreateRequest`에서 `author` 제거, 응답 DTO는 `authorName`을 `member.getUsername()`에서 파생. `@EntityGraph`에 `member` fetch 추가(N+1 재발 방지, Phase 8 연계)
- [ ] **TDD 순서**: ①`MemberRepository`(email 중복) → ②`MemberService` 회원가입(비번 인코딩/중복 예외) → ③JWT 유틸(만료·변조) → ④`spring-security-test`로 미인증 401·인증 생성 201 → ⑤기존 Post/Comment 컨트롤러·통합 테스트를 author 전송 → 인증 principal 기반으로 먼저 Red 전환 후 프로덕션 변경
- **배우는 것**: 필터체인 순서, `SecurityContextHolder`, 세션 vs JWT(stateless), BCrypt/단방향 해시, 인증 vs 인가, stateless에서 CSRF off 이유
- **의존성**: `spring-boot-starter-security`, JWT(`io.jsonwebtoken:jjwt` — 직접 구현이 학습 효과 ↑), `spring-security-test`
- **검증**: 회원가입→로그인→토큰으로 게시글 작성 흐름, 미인증 요청 401, 전체 테스트 GREEN

### Phase 12: 소유권 기반 인가 (작성자만 수정/삭제)
Phase 11 직후 이어지는 소규모 Phase. 인증과 인가의 차이를 코드로 체득.
- [ ] `PostService`/`CommentService`의 update·delete에서 "현재 사용자 == 작성자" 검증, 아니면 403(`ErrorCode.FORBIDDEN`). `ADMIN` role은 우회 허용
- [ ] 심화: `@PreAuthorize`(`@EnableMethodSecurity`) vs 서비스 레이어 수동 검증 비교
- [ ] **TDD**: Service 테스트 "다른 사용자가 수정 시 ForbiddenException"(Red) → 검증 로직(Green), Controller 슬라이스 403 확인
- **배우는 것**: 인증 vs 인가, 도메인 권한 검증 vs `@PreAuthorize`, 403 vs 404 정책(존재 노출 회피)
- **검증**: 타인 게시글 수정/삭제 시 403, 본인/ADMIN은 정상

### Phase 13: 실 DB(PostgreSQL) + Flyway + docker-compose + 프로파일·감사·soft delete
H2 인메모리 졸업(사용자 결정). Phase 9(프로파일/로깅/OSIV)를 여기에 합쳐 마무리.
- [ ] `docker-compose.yml`로 PostgreSQL 기동
- [ ] Flyway `V1__init.sql`로 누적 스키마 명시(ddl-auto 의존 탈피), `prod`는 `ddl-auto=validate`
- [ ] 프로파일: `dev`(로컬 PG/H2), `prod`(PG + validate), `test`(Testcontainers/H2). Phase 9 항목(OSIV=false, `@Slf4j` 로깅, `GlobalExceptionHandler`의 `log.error`) 완료
- [ ] **감사**: `BaseTimeEntity` 확장(`@CreatedBy`/`@LastModifiedBy`), `AuditorAware`가 `SecurityContext`에서 현재 사용자 제공(Phase 11 의존)
- [ ] **soft delete**: `@SQLRestriction` + `deletedAt` 컬럼. 기존 cascade REMOVE 정책 충돌 재설계 주의
- [ ] **TDD**: soft delete가 핵심 — `@DataJpaTest` "삭제 후 findAll 미포함 / DB엔 잔존"(Red) → 구현(Green). Flyway는 컨텍스트 로딩 통합 테스트로 검증
- **배우는 것**: `ddl-auto=validate`가 정석인 이유, 마이그레이션 툴 필요성, OSIV 트레이드오프, soft delete 장단점(유니크 제약/조회 필터 누락 위험), `AuditorAware`
- **의존성**: `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`
- **검증**: docker-compose 기동 후 prod 프로필 부팅(validate 통과), soft delete 동작 확인

### Phase 14: QueryDSL 동적 검색 + 도메인 확장(카테고리/태그/좋아요)
검색을 동적 쿼리로 끌어올리고, 그 가치를 보여줄 검색 조건을 위해 도메인을 확장.
- [ ] QueryDSL 도입, `PostRepositoryCustom` + `PostRepositoryImpl`로 동적 검색(키워드+작성자+카테고리+정렬), 기존 `searchByKeyword` 대체
- [ ] `Category`(ManyToOne) 또는 `Tag` — ManyToMany는 **중간 엔티티(`PostTag`)로 풀어쓰는 패턴** 권장
- [ ] 좋아요(`PostLike`, member+post 유니크 제약, 카운트 집계)
- [ ] N+1 재점검: `PostListResponse`를 COUNT 프로젝션 DTO 직접 조회로 전환(Phase 8 학습 노트의 "COUNT 서브쿼리 DTO projection" 실구현)
- [ ] **TDD**: 동적 검색 Repository 테스트(조건 조합별 결과) Red → 구현 Green. 좋아요 중복 예외(Red) → 유니크 제약/검증(Green)
- **배우는 것**: QueryDSL 타입세이프/동적 쿼리, `BooleanBuilder` vs `BooleanExpression`, ManyToMany를 중간 엔티티로 푸는 이유, 집계 쿼리, 유니크 제약으로 중복 방지
- **의존성**: QueryDSL(`com.querydsl:querydsl-jpa:...:jakarta`, Gradle Q타입 생성 설정 — Spring Boot 4 환경 주의)
- **검증**: 다중 조건 조합 검색 + 좋아요 중복 차단 테스트 GREEN

### Phase 15: Swagger/OpenAPI 문서화
API/인증이 안정된 뒤 문서화(재작업 최소). JWT 인증 헤더까지 반영.
- [ ] springdoc-openapi 도입, JWT `SecurityScheme` 등록(Authorize 버튼), `@Operation`/`@Schema` 어노테이션. `prod`에선 Swagger UI 비활성화
- [ ] **TDD**: 문서화는 적합도 낮음 — `/v3/api-docs` 200 + 보안 스킴 존재 smoke 통합 테스트로 충분
- **배우는 것**: API 문서 자동화, OpenAPI 스펙, 인증 헤더 문서화(포트폴리오 가시성 ↑)
- **의존성**: `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- **검증**: `/swagger-ui.html`에서 Authorize 후 인증 API 호출 가능

### Phase 16: CI(GitHub Actions) + Testcontainers + 조회수 동시성/Redis 캐싱
자동화와 실 DB 기반 테스트로 마무리. 동시성/캐싱을 인프라 성격으로 묶음.
- [ ] Testcontainers로 통합 테스트를 실제 PostgreSQL에서 실행(H2 방언 차이 제거), `PostIntegrationTest`를 `@Testcontainers`로 전환
- [ ] GitHub Actions: PR마다 `./gradlew test` + 빌드
- [ ] **조회수 동시성**: 현재 `incrementViewCount()`는 dirty checking이라 동시 요청에 lost update → 비관적 락 / `@Modifying` 원자적 UPDATE / Redis INCR 중 택1
- [ ] **Redis 캐싱**: 인기글/단건 조회 `@Cacheable` + TTL
- [ ] **TDD**: 조회수 — 멀티스레드 N회 동시 조회 후 viewCount==N 통합 테스트(Red, 현재 실패) → 원자적 UPDATE/락(Green). 캐시는 "2번째 조회 시 쿼리 미발생"을 Hibernate Statistics로 검증(Phase 8 기법 재활용)
- **배우는 것**: CI/CD 기본, Testcontainers가 H2보다 신뢰성 높은 이유, lost update와 동시성 제어(낙관/비관 락), 캐시 무효화 전략
- **의존성**: `org.testcontainers:postgresql`/`junit-jupiter`, `spring-boot-starter-data-redis`
- **검증**: 동시성 테스트 GREEN, GitHub Actions PR 체크 통과

#### 우선순위 요약

| 후보 | Phase | 비고 |
|---|---|---|
| 에러코드/예외 체계 | 10 (선행) | Security 전 응답 계약 안정화. 저비용 고효율 |
| Security + JWT + Member | **11 (최우선)** | 면접 최빈출 + 나머지 전제 |
| 소유권 인가 | 12 | 11의 후속, 소규모 |
| 실 DB + Flyway + compose + 감사/soft delete | 13 | 사용자 결정(실 DB 졸업) 반영 |
| QueryDSL + 카테고리/태그/좋아요 | 14 | 검색 고도화 + ManyToMany 학습 |
| Swagger | 15 | API 안정 후, 가시성 ↑ |
| CI/Testcontainers + 동시성/Redis | 16 | 자동화·동시성 마무리 |

---

## 면접 대비 핵심 개념 커버리지

| 개념 | 해당 Phase |
|------|-----------|
| Spring IoC / DI (생성자 주입) | Phase 2 |
| 계층형 아키텍처 | Phase 2 |
| JPA 엔티티 매핑 | Phase 1 |
| JPA 연관관계 (ManyToOne/OneToMany) | Phase 5 |
| LAZY vs EAGER, N+1 문제 | Phase 5 / **Phase 8** |
| Fetch Join, @EntityGraph | **Phase 8** |
| 양방향 매핑 + 편의 메서드 | **Phase 8** |
| @Transactional, dirty checking | Phase 2 |
| DTO 패턴 | Phase 2 |
| 예외 처리 (@RestControllerAdvice) | Phase 3 |
| Bean Validation | Phase 4 |
| 페이징, Spring Data 쿼리 | Phase 6 |
| 테스트 (단위/통합/슬라이스) | Phase 7 |
| Spring Profile | **Phase 9 / 13** |
| slf4j 로깅 | **Phase 9 / 13** |
| OSIV (Open Session In View) | **Phase 9 / 13** |
| 표준 에러코드 / 예외 계층 | **Phase 10** |
| Spring Security 필터체인 | **Phase 11** |
| JWT, BCrypt, 인증 vs 인가 | **Phase 11 / 12** |
| 소유권 기반 인가 (@PreAuthorize) | **Phase 12** |
| 마이그레이션 (Flyway), ddl-auto=validate | **Phase 13** |
| JPA Auditing (createdBy), soft delete | **Phase 13** |
| QueryDSL 동적 쿼리 | **Phase 14** |
| ManyToMany 중간 엔티티, 집계 쿼리 | **Phase 14** |
| OpenAPI / Swagger 문서화 | **Phase 15** |
| 동시성 제어 (낙관/비관 락, lost update) | **Phase 16** |
| Redis 캐싱 | **Phase 16** |
| CI/CD, Testcontainers | **Phase 16** |
