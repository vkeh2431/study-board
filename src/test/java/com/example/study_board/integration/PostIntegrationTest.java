package com.example.study_board.integration;

import com.example.study_board.domain.comment.CommentRepository;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.dto.comment.CommentCreateRequest;
import com.example.study_board.dto.comment.CommentResponse;
import com.example.study_board.dto.comment.CommentUpdateRequest;
import com.example.study_board.dto.post.PostCreateRequest;
import com.example.study_board.dto.post.PostResponse;
import com.example.study_board.dto.post.PostUpdateRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @BeforeEach
    void cleanUp() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
    }

    private Long createPostViaApi(String title, String content, String author) throws Exception {
        PostCreateRequest request = new PostCreateRequest(title, content, author);
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        PostResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), PostResponse.class);
        return response.id();
    }

    @Test
    @DisplayName("게시글 생성 후 단건 조회 시 조회수가 1 증가")
    void create_and_find_post_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용", "작성자");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("게시글 수정 후 변경된 내용이 조회됨")
    void update_post_full_flow() throws Exception {
        Long postId = createPostViaApi("기존 제목", "기존 내용", "작성자");
        PostUpdateRequest updateRequest = new PostUpdateRequest("수정된 제목", "수정된 내용");

        mockMvc.perform(put("/api/posts/" + postId)
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
    @DisplayName("게시글 삭제 시 댓글도 cascade 삭제")
    void delete_post_cascades_comments() throws Exception {
        Long postId = createPostViaApi("제목", "내용", "작성자");
        CommentCreateRequest commentRequest = new CommentCreateRequest("첫 댓글", "댓글 작성자");
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CommentCreateRequest("둘째 댓글", "댓글 작성자"))))
                .andExpect(status().isCreated());

        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(postId)).hasSize(2);

        mockMvc.perform(delete("/api/posts/" + postId))
                .andExpect(status().isNoContent());

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.findByPostIdOrderByCreatedAtDesc(postId)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404")
    void find_post_not_found_returns_404() throws Exception {
        mockMvc.perform(get("/api/posts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("댓글 CRUD 전체 흐름")
    void comment_crud_full_flow() throws Exception {
        Long postId = createPostViaApi("제목", "내용", "작성자");
        CommentCreateRequest createRequest = new CommentCreateRequest("댓글 내용", "댓글 작성자");

        MvcResult createResult = mockMvc.perform(post("/api/posts/" + postId + "/comments")
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
                .andExpect(jsonPath("$[0].content").value("댓글 내용"));

        CommentUpdateRequest updateRequest = new CommentUpdateRequest("수정된 댓글");
        mockMvc.perform(put("/api/comments/" + commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 댓글"));

        mockMvc.perform(delete("/api/comments/" + commentId))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(commentId)).isEmpty();
    }
}
