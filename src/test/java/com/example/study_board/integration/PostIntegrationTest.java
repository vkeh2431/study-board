package com.example.study_board.integration;

import com.example.study_board.domain.category.Category;
import com.example.study_board.domain.category.CategoryRepository;
import com.example.study_board.domain.comment.CommentRepository;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.auth.LoginRequest;
import com.example.study_board.dto.auth.TokenResponse;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostUpdateRequest;
import com.example.study_board.global.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PostIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = signupAndLogin("author@example.com", "작성자", "password123");
    }

    private String signupAndLogin(String email, String username, String password) throws Exception {
        SignupRequest signup = new SignupRequest(email, username, password);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse tokenResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        return tokenResponse.accessToken();
    }

    private Long createPostViaApi(String title, String content) throws Exception {
        return createPostViaApi(title, content, null, null);
    }

    private Long createPostViaApi(String title, String content, Long categoryId, List<String> tagNames) throws Exception {
        PostCreateRequest request = new PostCreateRequest(title, content, categoryId, tagNames);
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        PostResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), PostResponse.class);
        return response.id();
    }

    @Test
    @DisplayName("통합 테스트가 H2가 아닌 실제 MySQL 컨테이너에 연결된다")
    void runs_on_real_mysql() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains("mysql");
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualToIgnoringCase("MySQL");
        }
    }

    @Test
    @DisplayName("게시글 생성 후 단건 조회 시 조회수가 1 증가")
    void create_and_find_post_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.authorName").value("작성자"))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("인증 없이 게시글 작성 시 401")
    void create_post_unauthenticated_returns_401() throws Exception {
        PostCreateRequest request = new PostCreateRequest("제목", "내용", null, null);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("게시글 수정 후 변경된 내용이 조회됨")
    void update_post_full_flow() throws Exception {
        Long postId = createPostViaApi("기존 제목", "기존 내용");
        PostUpdateRequest updateRequest = new PostUpdateRequest("수정된 제목", "수정된 내용", null, null);

        mockMvc.perform(put("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.content").value("수정된 내용"));

        assertThat(postRepository.findById(postId)).hasValueSatisfying(p -> {
            assertThat(p.getTitle()).isEqualTo("수정된 제목");
            assertThat(p.getContent()).isEqualTo("수정된 내용");
        });
    }

    @Test
    @DisplayName("타인이 게시글 수정 시 403 + 원본 보존")
    void update_post_by_other_user_returns_403() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        String otherToken = signupAndLogin("other@example.com", "다른사람", "password123");
        PostUpdateRequest request = new PostUpdateRequest("해킹 제목", "해킹 내용", null, null);

        mockMvc.perform(put("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        assertThat(postRepository.findById(postId))
                .hasValueSatisfying(p -> assertThat(p.getTitle()).isEqualTo("제목"));
    }

    @Test
    @DisplayName("타인이 게시글 삭제 시 403 + 원본 보존")
    void delete_post_by_other_user_returns_403() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        String otherToken = signupAndLogin("other@example.com", "다른사람", "password123");

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        assertThat(postRepository.findById(postId)).isPresent();
    }

    @Test
    @DisplayName("게시글 삭제 시 댓글도 cascade 삭제")
    void delete_post_cascades_comments() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest("첫 댓글"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentCreateRequest("둘째 댓글"))))
                .andExpect(status().isCreated());

        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(postId)).hasSize(2);

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(postId)).isEmpty();
    }

    @Test
    @DisplayName("카테고리·태그와 함께 작성하면 상세 조회에 노출된다")
    void create_with_category_and_tags_full_flow() throws Exception {
        Long categoryId = categoryRepository.save(Category.builder().name("스프링").build()).getId();
        Long postId = createPostViaApi("제목", "내용", categoryId, List.of("java", "spring"));

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("스프링"))
                .andExpect(jsonPath("$.tagNames", containsInAnyOrder("java", "spring")));
    }

    @Test
    @DisplayName("태그로 목록 검색")
    void search_posts_by_tag() throws Exception {
        createPostViaApi("자바 글", "내용", null, List.of("java"));
        createPostViaApi("파이썬 글", "내용", null, List.of("python"));

        mockMvc.perform(get("/api/posts").param("tag", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("자바 글"));
    }

    @Test
    @DisplayName("좋아요 후 상세 조회 시 likeCount=1, 본인은 liked=true")
    void like_post_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용");

        mockMvc.perform(post("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(true));
    }

    @Test
    @DisplayName("같은 사용자가 두 번 좋아요하면 409(ALREADY_LIKED)")
    void like_twice_returns_409() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        mockMvc.perform(post("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_LIKED.getCode()));
    }

    @Test
    @DisplayName("좋아요 취소 후 likeCount=0")
    void unlike_post_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        mockMvc.perform(post("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    @DisplayName("비로그인 상세 조회는 liked=false")
    void anonymous_detail_liked_false() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        mockMvc.perform(post("/api/posts/" + postId + "/likes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/" + postId)) // 토큰 없이
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    @DisplayName("인증 없이 좋아요하면 401")
    void like_unauthenticated_returns_401() throws Exception {
        Long postId = createPostViaApi("제목", "내용");

        mockMvc.perform(post("/api/posts/" + postId + "/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404")
    void find_post_not_found_returns_404() throws Exception {
        mockMvc.perform(get("/api/posts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("댓글 CRUD 전체 흐름")
    void comment_crud_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용");
        CommentCreateRequest createRequest = new CommentCreateRequest("댓글 내용");

        MvcResult createResult = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        CommentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CommentResponse.class);
        Long commentId = created.id();

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("댓글 내용"))
                .andExpect(jsonPath("$[0].authorName").value("작성자"));

        CommentUpdateRequest updateRequest = new CommentUpdateRequest("수정된 댓글");
        mockMvc.perform(put("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 댓글"));

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(commentId)).isEmpty();
    }
}
