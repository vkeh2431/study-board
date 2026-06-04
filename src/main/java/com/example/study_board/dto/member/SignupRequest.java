package com.example.study_board.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "이메일(로그인 ID, 중복 불가)", example = "user@example.com")
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        @Schema(description = "표시용 사용자명(최대 50자)", example = "honggildong")
        @NotBlank(message = "사용자명은 필수입니다")
        @Size(max = 50, message = "사용자명은 50자 이하여야 합니다")
        String username,

        @Schema(description = "비밀번호(8~100자, BCrypt로 해시 저장)", example = "password1234")
        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다")
        String password
) {
}
