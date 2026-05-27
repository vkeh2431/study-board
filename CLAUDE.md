# study_board

Spring Boot 게시판 학습 프로젝트. REST API 전용, H2 인메모리 DB.

## TDD 규칙 (반드시 준수)

### 핵심 원칙: Red-Green-Refactor

새로운 기능을 구현할 때 반드시 아래 순서를 따른다:

1. **Red** — 실패하는 테스트를 먼저 작성한다. 프로덕션 코드를 먼저 작성하지 않는다.
2. **Green** — 테스트를 통과시키는 최소한의 프로덕션 코드를 작성한다.
3. **Refactor** — 테스트가 통과하는 상태를 유지하면서 코드를 개선한다.

기존 코드를 수정할 때도 변경할 동작에 대한 테스트를 먼저 작성(또는 수정)한 뒤 구현을 변경한다.

### 레이어별 테스트 어노테이션

| 레이어 | 어노테이션 | 핵심 도구 |
|--------|-----------|-----------|
| Repository | `@DataJpaTest` + `@Import(JpaAuditingConfig.class)` | 실제 DB (H2), `assertThat()` |
| Service | `@ExtendWith(MockitoExtension.class)` | `@Mock`, `@InjectMocks`, `given()`, `verify()` |
| Controller | `@WebMvcTest(XxxController.class)` | `MockMvc`, `@MockBean`, `ObjectMapper` |

### 테스트 컨벤션

- `@DisplayName`은 한글로 작성한다 (예: `@DisplayName("게시글 저장")`)
- 메서드명은 영문 snake_case로 작성한다 (예: `save_post()`)
- AssertJ `assertThat()`만 사용한다 (JUnit의 assertEquals 사용하지 않는다)
- Given/When/Then 구조로 작성하되, 주석 없이 빈 줄로 구분한다
- 엔티티 생성은 `createPost()` 같은 헬퍼 메서드에 builder 패턴을 사용한다
- 테스트 클래스는 대상 클래스와 같은 패키지에 위치한다

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "com.example.study_board.domain.post.PostServiceTest"
```

## 프로젝트 구조

```
src/main/java/com/example/study_board/
├── domain/{도메인}/    Entity, Repository, Service, Controller
├── dto/{도메인}/       Request/Response DTO (Java record)
├── global/exception/   GlobalExceptionHandler, ErrorResponse, ResourceNotFoundException
├── global/config/      JpaAuditingConfig
└── common/             BaseTimeEntity

src/test/java/          프로덕션 코드와 동일한 패키지 구조
```

## 코드 컨벤션

- DTO는 Java `record`로 작성한다
- 검증은 `@NotBlank`, `@Size` 등 Bean Validation 사용
- Service에 `@Transactional(readOnly = true)` 클래스 레벨, 쓰기 메서드에 `@Transactional`
- 생성자 주입 (`@RequiredArgsConstructor`)
- 엔티티에 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
