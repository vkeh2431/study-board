# Spring Boot 게시판 (study_board) 구현 계획

## Context
Spring Boot 신입 개발자 취업 준비를 위한 학습 프로젝트. FastAPI/PHP 백엔드 1년 경험을 바탕으로, Spring Boot의 핵심 개념과 면접 빈출 주제를 게시판 구현을 통해 익힌다. REST API 전용(Thymeleaf 없음), H2 인메모리 DB 사용.

---

## DB 스키마

**post** 테이블: `id(PK)`, `title`, `content`, `author`, `view_count`, `created_at`, `updated_at`
**comment** 테이블: `id(PK)`, `post_id(FK)`, `content`, `author`, `created_at`, `updated_at`
- Post 1 : N Comment (게시글 삭제 시 댓글 cascade 삭제)

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
- [ ] `ResourceNotFoundException` (커스텀 예외)
- [ ] `ErrorResponse` (표준 에러 응답 DTO)
- [ ] `GlobalExceptionHandler` (@RestControllerAdvice)
- [ ] PostService에서 커스텀 예외 적용
- **배우는 것**: @RestControllerAdvice, @ExceptionHandler, 표준 에러 응답 포맷
- **검증**: 존재하지 않는 ID 조회 시 404 JSON 응답 확인

### Phase 4: Validation
- [ ] `build.gradle`에 `spring-boot-starter-validation` 의존성 추가
- [ ] Request DTO에 @NotBlank, @Size 등 검증 어노테이션 추가
- [ ] Controller에 @Valid 적용
- **배우는 것**: Bean Validation, @NotBlank vs @NotNull vs @NotEmpty 차이 (면접 빈출)
- **검증**: 빈 값으로 POST 요청 시 400 에러 + 필드별 에러 메시지 확인

### Phase 5: Comment 엔티티 + CRUD
- [ ] `Comment` 엔티티 (@ManyToOne(fetch=LAZY), @JoinColumn)
- [ ] `Post`에 @OneToMany(mappedBy, cascade=REMOVE) 추가
- [ ] `CommentRepository` (findByPostIdOrderByCreatedAtDesc 쿼리 메서드)
- [ ] CommentService, CommentController, DTO 구현
- **배우는 것**: JPA 연관관계 (면접 최빈출), LAZY vs EAGER, cascade, 쿼리 메서드 네이밍
- **검증**: 댓글 CRUD + 게시글 삭제 시 댓글 cascade 삭제 확인

### Phase 6: 페이징 + 검색
- [ ] `PostRepository`에 `@Query`로 제목+내용 검색 추가
- [ ] PostService/Controller에 keyword 파라미터 연동
- **배우는 것**: Pageable, Page<T>, @Query(JPQL), @Param, @PageableDefault
- **검증**: 15개 이상 게시글 생성 후 페이징/검색 테스트

### Phase 7: 테스트 코드
- [ ] `PostRepositoryTest` (@DataJpaTest)
- [ ] `PostServiceTest` (Mockito - @Mock, @InjectMocks)
- [ ] `PostControllerTest` (@WebMvcTest + MockMvc)
- [ ] `CommentServiceTest` (Mockito)
- [ ] `PostIntegrationTest` (@SpringBootTest + @AutoConfigureMockMvc)
- **배우는 것**: 슬라이스 테스트, MockMvc, Mockito, 테스트 피라미드
- **검증**: `./gradlew test` 전체 통과

---

## 면접 대비 핵심 개념 커버리지

| 개념 | 해당 Phase |
|------|-----------|
| Spring IoC / DI (생성자 주입) | Phase 2 |
| 계층형 아키텍처 | Phase 2 |
| JPA 엔티티 매핑 | Phase 1 |
| JPA 연관관계 (ManyToOne/OneToMany) | Phase 5 |
| LAZY vs EAGER, N+1 문제 | Phase 5 |
| @Transactional, dirty checking | Phase 2 |
| DTO 패턴 | Phase 2 |
| 예외 처리 (@RestControllerAdvice) | Phase 3 |
| Bean Validation | Phase 4 |
| 페이징, Spring Data 쿼리 | Phase 6 |
| 테스트 (단위/통합/슬라이스) | Phase 7 |
