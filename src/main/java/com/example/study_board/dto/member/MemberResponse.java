package com.example.study_board.dto.member;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.Role;

public record MemberResponse(
        Long id,
        String email,
        String username,
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
