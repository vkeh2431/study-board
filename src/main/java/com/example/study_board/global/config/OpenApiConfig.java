package com.example.study_board.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 문서 설정 (Phase 15).
 *
 * <p>JWT(Bearer) {@link SecurityScheme}를 등록해 Swagger UI에 "Authorize" 버튼을 만든다.
 * 토큰은 {@code Authorization: Bearer <jwt>} 헤더로 전달되며,
 * {@link com.example.study_board.global.security.JwtAuthenticationFilter}가 검증한다.
 *
 * <p>전역 {@link SecurityRequirement}는 모든 작업에 자물쇠를 표시하기 위한 것이며,
 * 공개 엔드포인트(GET /api/posts, POST /api/auth/**)는 토큰 없이도 호출 가능하다.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI studyBoardOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("study_board API")
                        .version("v1")
                        .description("Spring Boot 게시판 학습 프로젝트 REST API. 우상단 Authorize에 로그인으로 발급받은 JWT를 입력하면 보호된 API를 호출할 수 있다."))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
