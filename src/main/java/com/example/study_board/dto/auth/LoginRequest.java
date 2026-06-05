package com.example.study_board.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "가입한 이메일", example = "user@example.com")
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        // 로그인 비밀번호에는 의도적으로 @Size를 두지 않는다. 길이 정책은 가입(SignupRequest)에서만 강제하고,
        // 로그인은 저장된 해시와 대조만 한다. 여기에 길이 제약을 걸면 정책 변경 시 기존 계정이 막히고 정책이 노출된다.
        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank(message = "비밀번호는 필수입니다")
        String password
) {
}
