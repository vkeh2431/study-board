package com.example.study_board.global.security;

import com.example.study_board.domain.member.Role;
import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @WebMvcTest 슬라이스에서 커스텀 principal({@link CustomUserDetails})로 인증 컨텍스트를 구성한다.
 * Spring Security 기본 @WithMockUser는 principal이 User 타입이라
 * @AuthenticationPrincipal CustomUserDetails로 주입되지 않으므로 별도 메타애너테이션이 필요하다.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory.class)
public @interface WithMockCustomUser {

    long memberId() default 1L;

    String email() default "test@test.com";

    String username() default "작성자";

    Role role() default Role.USER;
}
