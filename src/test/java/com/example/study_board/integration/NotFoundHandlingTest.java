package com.example.study_board.integration;

import com.example.study_board.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotFoundHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("매핑되지 않은(공개) 경로는 500이 아니라 404를 반환한다")
    void unmapped_public_path_returns_404() throws Exception {
        // GET /api/posts/** 는 permitAll이라 시큐리티를 통과해 디스패처까지 도달하지만
        // 매핑된 핸들러가 없어 NoResourceFoundException이 발생한다. 500이 아닌 404여야 한다.
        mockMvc.perform(get("/api/posts/x/y/z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }
}
