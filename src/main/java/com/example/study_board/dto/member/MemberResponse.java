package com.example.study_board.dto.member;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
        @Schema(description = "회원 ID", example = "1")
        Long id,

        @Schema(description = "이메일", example = "user@example.com")
        String email,

        @Schema(description = "사용자명", example = "honggildong")
        String username,

        @Schema(description = "권한", example = "USER")
        Role role
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getUsername(),
                member.getRole()
        );
    }
}
