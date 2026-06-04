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
- [x] `application-dev.properties` 신설 (현재 H2 메모리/show-sql/h2-console 설정 이관)
- [x] `application-prod.properties` 신설 (`ddl-auto=validate`, `show-sql=false` 등) — ⚠️ 빈 H2+validate라 지금 부팅하면 실패하는 학습용 플레이스홀더(실 부팅은 Phase 13)
- [x] `application.properties`는 공통 항목만 남기고 `spring.profiles.active=dev` 설정
- [x] `src/test/resources/application-test.properties` (Phase 8에서 생성됨) — test 프로필 미상속 대비 `ddl-auto=create-drop` 보강
- [x] `PostService`, `CommentService`에 `@Slf4j` + `log.info` 추가 (생성/수정/삭제 이벤트)
- [x] `GlobalExceptionHandler`의 `handleException`에 `log.error("처리되지 않은 예외 발생", e)` 추가 (예외 삼킴 해결) + 4xx 핸들러는 `log.warn`으로 레벨 구분
- [x] `application.properties`에 `spring.jpa.open-in-view=false` 명시 (부팅 시 OSIV 기본 경고 사라짐 확인)
- [x] (선택) `PostIntegrationTest`에 `@Transactional`로 자동 롤백 적용 — N+1 측정은 별도 클래스(`PostQueryPerformanceTest`)라 충돌 없음. `@BeforeEach deleteAll()` 제거, `@ActiveProfiles("test")` 함께 적용(`StudyBoardApplicationTests`도 동일)
- **배우는 것**: Spring Profile, slf4j 로깅 레벨, OSIV(Open Session In View)와 트레이드오프, 통합 테스트 자동 롤백
- **검증**: ✅ 전체 테스트 GREEN + dev 프로필 부팅(active=dev, OSIV 경고 없음, /h2-console) + 생성/조회(viewCount)/없는 ID 404/검증 400 curl 확인, 로그(`게시글 생성 완료`/`리소스 없음`/`입력값 검증 실패`) 출력 확인
  - ⚠️ 학습 노트: 통합 테스트에 `@Transactional`을 붙이면 테스트 트랜잭션이 직렬화 시점까지 열려 있어 OSIV=false여도 `LazyInitializationException`을 잡지 못함 → OSIV 비활성화 안전성은 "DTO를 서비스 트랜잭션 안에서 완성하는 설계"가 보장

---

## 프로덕션화 로드맵 (Phase 10~16)

Phase 0~9로 학습 기초가 정리된 뒤, "프로덕션 같은" 프로젝트로 발전시키기 위한 로드맵.
**설계 원칙**: ① 인증/Member를 먼저(이후 후보들의 전제) ② 에러 계약을 Security 앞에 정리 ③ 모든 Phase는 기존 TDD(Red-Green-Refactor) 규칙 유지. `author` 제거처럼 기존 테스트가 깨지는 변경은 "테스트를 먼저 Red로 수정 → 프로덕션 코드 변경" 순서로 진행한다.
**사용자 결정 반영**: 실 DB(MySQL + Docker)로 졸업(채용 공고 빈도 반영 — PostgreSQL → MySQL 변경), 인증/인가(Spring Security)를 최우선 중점 주제로.

### Phase 10: 공통 에러코드 enum + 예외 체계 정리
Security를 얹기 전 응답/에러 계약을 안정화. 작지만 모든 후속 Phase가 이 위에 쌓인다.
- [x] `ErrorCode` enum 도입 (code 문자열, `HttpStatus`, defaultMessage 보유) — 흩어진 `"RESOURCE_NOT_FOUND"`, `"VALIDATION_ERROR"` 문자열 통합
- [x] `BusinessException` 베이스 예외(`ErrorCode` 보유) 도입, `ResourceNotFoundException`을 이 체계로 편입
- [x] `GlobalExceptionHandler`를 `ErrorCode` 기반으로 리팩터 (단일 `@ExceptionHandler(BusinessException)` 다형 처리로 통합, `ErrorResponse.of(ErrorCode)` 팩터리 추가)
- [x] **TDD**: Controller/통합 테스트 에러 케이스 ~12곳을 `ErrorCode.X.getCode()` 참조로 먼저 수정(Red) → 핸들러/예외 리팩터(Green) + `ErrorCodeTest`/`BusinessExceptionTest` 단위 테스트
- **배우는 것**: 에러 응답 일관성, enum + `@RestControllerAdvice` 조합, 예외 계층 설계
- **검증**: ✅ 없는 ID 조회 / 검증 실패 시 통일된 `code` 필드 JSON 응답 + 전체 테스트 GREEN (69개)
- ⚠️ 성공 응답 `ApiResponse<T>` 전체 래핑은 `Page<T>` 직렬화 충돌·면접 호불호로 **보류**(에러 포맷 통일만)

### Phase 11: Spring Security + JWT + Member 도메인 [최우선 핵심]
가장 크고 중요한 Phase. 신입~주니어 면접 최빈출(인증/인가, 필터체인)이며 나머지 절반의 전제.
- [x] `Member` 엔티티(email/username, password(BCrypt), `Role` enum) + `MemberRepository`
- [x] 회원가입 / 로그인 API, JWT 발급(access + refresh) — refresh는 `type=refresh` 클레임 stateless JWT, `POST /api/auth/refresh`
- [x] `SecurityConfig`(`SecurityFilterChain` bean), `JwtAuthenticationFilter`(JWT 검증 → `CustomUserDetailsService.loadByMemberId`로 principal 복원), `CustomUserDetails`/`CustomUserDetailsService`. 필터 단계 401/403은 `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`가 Phase 10 에러 계약(JSON) 유지
- [x] **author 마이그레이션**: `Post.author(String)` → `Post.member(@ManyToOne(LAZY))`, `Comment` 동일. 작성자는 `@AuthenticationPrincipal CustomUserDetails`에서 주입, `PostCreateRequest`/`CommentCreateRequest`에서 `author` 제거, 응답 DTO는 `authorName`을 `member.getUsername()`에서 파생. `@EntityGraph`에 `member` fetch 추가(N+1 재발 방지, Phase 8 연계)
- [x] **TDD 순서**: ①`MemberRepository`(email 중복) → ②`MemberService` 회원가입(비번 인코딩/중복 예외) → ③JWT 유틸(만료·변조) → ④`spring-security-test`로 미인증 401·인증 생성 201 → ⑤기존 Post/Comment 컨트롤러·통합 테스트를 author 전송 → 인증 principal 기반으로 먼저 Red 전환 후 프로덕션 변경
- **배우는 것**: 필터체인 순서, `SecurityContextHolder`, 세션 vs JWT(stateless), BCrypt/단방향 해시, 인증 vs 인가, stateless에서 CSRF off 이유
- **의존성**: `spring-boot-starter-security`, `io.jsonwebtoken:jjwt 0.12.6`(api/impl/jackson), `spring-boot-starter-security-test`(Boot 4 모듈러 테스트 스타터)
- **검증**: ✅ 회원가입→로그인→토큰으로 게시글 작성(authorName 주입)→미인증 401(UNAUTHORIZED)→읽기 공개→refresh→변조 토큰 401→중복 이메일 409 curl 확인 + 전체 테스트 GREEN(98개) + dev 프로필 부팅(기본 보안 비밀번호 미생성=SecurityConfig 적용, /h2-console 접근)
  - ⚠️ Boot 4 노트: `spring-boot-starter-security-test`가 클래스패스에 있으면 `@WebMvcTest`가 보안을 강제 → 슬라이스 쓰기 테스트는 커스텀 `@WithMockCustomUser`(principal=`CustomUserDetails`)로 인증, CSRF off라 csrf() 불필요. 기본 `@WithMockUser`는 principal이 `User`라 `@AuthenticationPrincipal CustomUserDetails`로 안 들어감
  - ⚠️ jjwt 0.12 API: `verifyWith()`/`parseSignedClaims().getPayload()`/`signWith(key)`. jjwt-jackson은 Jackson 2를 transitive로 끌어오지만 내부 전용이라 Spring Boot 4 Jackson 3와 격리 공존. 커스텀 핸들러는 Jackson 3(`tools.jackson...ObjectMapper`) 주입
  - ⚠️ Spring Security 7: `DaoAuthenticationProvider`는 `new DaoAuthenticationProvider(userDetailsService)` 생성자 + `setPasswordEncoder` (무인자 생성자 제거)
  - ⚠️ 소유권 인가(작성자만 수정/삭제)는 Phase 12, `@CreatedBy` 감사 컬럼은 Phase 13 범위

### Phase 12: 소유권 기반 인가 (작성자만 수정/삭제)
Phase 11 직후 이어지는 소규모 Phase. 인증과 인가의 차이를 코드로 체득.
- [x] `PostService`/`CommentService`의 update·delete에서 "현재 사용자 == 작성자" 검증, 아니면 403(`ErrorCode.FORBIDDEN`). `ADMIN` role은 우회 허용. 컨트롤러는 `@AuthenticationPrincipal`에서 `getMemberId()`/`getRole()`을 서비스로 전달(시그니처 `update(id, memberId, role, request)`), 검증은 엔티티 `isOwner(memberId)` + 서비스 `verifyOwnership`. 신규 `ForbiddenException extends BusinessException`
- [x] 심화: `@PreAuthorize`(`@EnableMethodSecurity`) vs 서비스 레이어 수동 검증 비교 — **서비스 수동 검증으로 구현**하고 `@PreAuthorize` 비교는 학습 노트(`ForbiddenException` javadoc)로만 기록(`@EnableMethodSecurity` 미적용)
- [x] **TDD**: Service 테스트 "다른 사용자가 수정 시 ForbiddenException"(Red) → 검증 로직(Green), Controller 슬라이스 403 확인. 단위 테스트는 `ReflectionTestUtils.setField(member,"id",..)`로 작성자 식별자 부여
- **배우는 것**: 인증 vs 인가, 도메인 권한 검증 vs `@PreAuthorize`, 403 vs 404 정책(존재 노출 회피)
- **검증**: ✅ 타인 게시글/댓글 수정·삭제 시 403(FORBIDDEN), 본인/ADMIN은 정상 + 전체 테스트 GREEN(113개, +15)
  - ⚠️ 순서 노트: `findById`(404) → `verifyOwnership`(403) 순으로 없는 리소스는 항상 404 우선. LAZY `member` 프록시의 `getId()`는 FK에서 읽혀 추가 SELECT/초기화 없이 소유권 비교(OSIV=false 안전)
  - ⚠️ 소유권 403(`BusinessException`→`GlobalExceptionHandler`)과 필터 단계 403(`RestAccessDeniedHandler`)은 경로가 다르지만 둘 다 `code:"FORBIDDEN"`으로 일관

### Phase 13: 실 DB(MySQL) + Flyway + docker-compose + 프로파일·감사·soft delete
H2 인메모리 졸업(사용자 결정). Phase 9(프로파일/로깅/OSIV)를 여기에 합쳐 마무리.
- [ ] `docker-compose.yml`로 MySQL 기동 (`mysql:8.4`, 포트 3306, `CHARSET=utf8mb4`/`utf8mb4_unicode_ci`, `MYSQL_DATABASE`/`MYSQL_USER`/`MYSQL_PASSWORD` env)
- [ ] Flyway `V1__init.sql`로 누적 스키마 명시(ddl-auto 의존 탈피), `prod`는 `ddl-auto=validate`. PK는 `BIGINT AUTO_INCREMENT`(엔티티가 이미 `GenerationType.IDENTITY`), 타임스탬프는 `DATETIME(6)`, 테이블 옵션 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
- [ ] 프로파일: `dev`(로컬 MySQL/H2), `prod`(MySQL + validate), `test`(Testcontainers/H2). Phase 9 항목(OSIV=false, `@Slf4j` 로깅, `GlobalExceptionHandler`의 `log.error`) 완료
- [ ] **감사**: `BaseTimeEntity` 확장(`@CreatedBy`/`@LastModifiedBy`), `AuditorAware`가 `SecurityContext`에서 현재 사용자 제공(Phase 11 의존)
- [ ] **soft delete**: `@SQLRestriction` + `deletedAt` 컬럼. 기존 cascade REMOVE 정책 충돌 재설계 주의
- [ ] **TDD**: soft delete가 핵심 — `@DataJpaTest` "삭제 후 findAll 미포함 / DB엔 잔존"(Red) → 구현(Green). Flyway는 컨텍스트 로딩 통합 테스트로 검증
- **배우는 것**: `ddl-auto=validate`가 정석인 이유, 마이그레이션 툴 필요성, OSIV 트레이드오프, soft delete 장단점(유니크 제약/조회 필터 누락 위험), `AuditorAware`. MySQL 실무 포인트 — `utf8mb4`(이모지/한글 보조문자 저장)와 collation, `DATETIME(6)` vs `TIMESTAMP`, MySQL은 시퀀스 미지원이라 `IDENTITY`(AUTO_INCREMENT)가 정답
- **의존성**: `flyway-core`, `flyway-mysql`, `com.mysql:mysql-connector-j`
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
- [ ] Testcontainers로 통합 테스트를 실제 MySQL에서 실행(H2 방언 차이 제거), `PostIntegrationTest`를 `@Testcontainers`로 전환
- [ ] GitHub Actions: PR마다 `./gradlew test` + 빌드
- [ ] **조회수 동시성**: 현재 `incrementViewCount()`는 dirty checking이라 동시 요청에 lost update → 비관적 락 / `@Modifying` 원자적 UPDATE / Redis INCR 중 택1
- [ ] **Redis 캐싱**: 인기글/단건 조회 `@Cacheable` + TTL
- [ ] **TDD**: 조회수 — 멀티스레드 N회 동시 조회 후 viewCount==N 통합 테스트(Red, 현재 실패) → 원자적 UPDATE/락(Green). 캐시는 "2번째 조회 시 쿼리 미발생"을 Hibernate Statistics로 검증(Phase 8 기법 재활용)
- **배우는 것**: CI/CD 기본, Testcontainers가 H2보다 신뢰성 높은 이유, lost update와 동시성 제어(낙관/비관 락), 캐시 무효화 전략
- **의존성**: `org.testcontainers:mysql`/`junit-jupiter`, `spring-boot-starter-data-redis`
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

## 기능 확장 로드맵 (Phase 17~18)

프로덕션 골격(Phase 0~16)이 완성된 뒤, "기능 개수"가 아니라 **새로운 백엔드 개념**을 한 겹 더 쌓기 위한 기능 확장. 두 Phase는 *댓글 생성 → 이벤트 발행 → 알림 생성*으로 자연스럽게 이어진다. 둘 다 `Member` 도메인(Phase 11)에 의존한다.

### Phase 17: 대댓글 (계층형 댓글)
게시판에 가장 자연스러운 확장. JPA 자기참조(self-join)와 트리 직렬화를 익힌다.
- [ ] `Comment`에 자기참조 추가: `parent`(`@ManyToOne(fetch=LAZY)`, 자기 자신 참조), `children`(`@OneToMany(mappedBy="parent")`). **인접 리스트(adjacency list) 모델**
- [ ] depth 정책 결정: 단순화를 위해 **1-depth(댓글→대댓글)까지만** 허용(루트 댓글에만 대댓글 가능) 또는 무제한 중 택1 — 학습 후 README에 트레이드오프 기록
- [ ] 대댓글 작성 API: `POST /api/posts/{postId}/comments`에 `parentId`(nullable) 추가, parent의 post 일치 검증
- [ ] 조회 시 트리 구성: 루트 댓글 + `replies` 재귀 구조 응답 DTO(`CommentResponse`에 `List<CommentResponse> replies`). 계층 댓글 **N+1 주의**(Phase 8 연계 — `@EntityGraph` 또는 한 번에 로딩 후 메모리에서 트리 조립)
- [ ] **삭제 정책**: 자식이 있는 부모 댓글 삭제 시 물리 삭제 대신 `"삭제된 댓글입니다"` soft delete 패턴(Phase 13의 `deletedAt` 연계). 자식 없으면 실제 삭제
- [ ] **TDD 순서**: ①`@DataJpaTest`로 자기참조 저장·부모-자식 조회(Red→Green) → ②Service 트리 구성 로직(평면 리스트 → 트리, 단위 테스트) → ③Controller `parentId`로 대댓글 작성 슬라이스 → ④자식 있는 부모 삭제 시 soft delete 동작
- **배우는 것**: 자기참조 연관관계, 인접 리스트 vs 경로 열거(path enumeration) vs 클로저 테이블 비교(면접 포인트), 트리 직렬화, 계층 댓글 N+1, 부모 삭제 정책
- **검증**: 댓글에 대댓글 작성 → 트리 형태 응답, depth 정책 동작, 자식 있는 부모 삭제 시 본문만 가려지고 트리 유지

### Phase 18: 알림 (이벤트 기반 + 비동기)
"내 글/댓글에 댓글이 달리면 알림". 댓글 로직과 알림 로직을 **이벤트로 분리(decoupling)**하는 설계 감각이 핵심.
- [ ] `Notification` 엔티티: 수신자(`@ManyToOne Member`), `type`(enum: COMMENT_ON_POST 등), `message`, `read`(boolean), 연관 리소스 id(postId 등), `createdAt`
- [ ] **이벤트 발행**: `CommentService.create`에서 `ApplicationEventPublisher`로 `CommentCreatedEvent` 발행(알림 생성 로직을 직접 호출하지 않는다 — 결합 제거)
- [ ] **이벤트 수신**: `@TransactionalEventListener(phase = AFTER_COMMIT)`로 댓글 **커밋 후** 알림 생성 + `@Async`로 비동기 처리(`@EnableAsync`). "왜 AFTER_COMMIT인가"(롤백 시 알림 안 감), "별도 스레드의 트랜잭션·영속성 컨텍스트 분리 주의" 기록
- [ ] **본인 예외**: 본인 글에 본인이 댓글 → 알림 생성 안 함
- [ ] 알림 API: `GET /api/notifications`(내 알림 목록, 미읽음 우선), `PATCH /api/notifications/{id}/read`(읽음 처리). 본인 알림만 접근(Phase 12 소유권 인가 연계)
- [ ] (선택) **SSE 실시간 푸시**: `SseEmitter`로 미읽음 알림 실시간 전달 — 여유 시
- [ ] **TDD 순서**: ①`CommentService.create` 호출 시 이벤트 발행 검증(`ApplicationEvents` 또는 publisher mock, Red→Green) → ②리스너가 `CommentCreatedEvent` 수신 시 알림 생성(타인 글) / 본인 글 댓글은 미생성 → ③알림 목록·읽음 처리 Controller 슬라이스 → ④(선택) SSE 수신 통합 테스트
- **배우는 것**: `ApplicationEventPublisher`/`@EventListener`, `@TransactionalEventListener`(AFTER_COMMIT) 트랜잭션 경계, `@Async` 비동기와 별도 스레드의 영속성 컨텍스트 함정, 도메인 이벤트 패턴(결합도 낮추기), (선택) SSE
- **의존성**: 코어(추가 의존성 없음). SSE는 spring-web 기본 제공
- **검증**: 타인이 내 글에 댓글 → 알림 생성, 본인 댓글 → 알림 없음, 읽음 처리 동작, (선택) SSE로 실시간 수신

#### 우선순위 요약 (기능 확장)

| 후보 | Phase | 비고 |
|---|---|---|
| 대댓글(계층형 댓글) | 17 | 게시판 핵심 깊이, 자기참조·트리. Member(11)·soft delete(13) 연계 |
| 알림(이벤트 기반) | 18 | 설계 감각 어필(decoupling). 17의 댓글 흐름과 자연 연결 |

> 💡 17·18은 **취업 필수가 아니라 차별화**다. Phase 0~16(특히 11)이 우선. 둘 다 "기능 추가"보다 *자기참조/이벤트 기반*이라는 **새 개념 학습**이 목적이므로, 면접에서 설명할 수 있을 만큼 깊게 판다.

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
| 자기참조 연관관계, 계층형(트리) 데이터 | **Phase 17** |
| 도메인 이벤트, @TransactionalEventListener, @Async | **Phase 18** |
