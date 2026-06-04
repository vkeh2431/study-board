package com.example.study_board.domain.member;

import com.example.study_board.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    private Member createMember(String email, String username) {
        return Member.builder()
                .email(email)
                .username(username)
                .password("encoded-password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("이메일로 회원 존재 여부 확인 - 존재하면 true")
    void existsByEmail_returns_true_when_present() {
        memberRepository.save(createMember("user@example.com", "유저"));

        boolean exists = memberRepository.existsByEmail("user@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("이메일로 회원 존재 여부 확인 - 없으면 false")
    void existsByEmail_returns_false_when_absent() {
        boolean exists = memberRepository.existsByEmail("none@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("이메일로 회원 조회")
    void findByEmail_returns_member() {
        memberRepository.save(createMember("user@example.com", "유저"));

        Optional<Member> found = memberRepository.findByEmail("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("유저");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("존재하지 않는 이메일 조회 시 빈 Optional")
    void findByEmail_returns_empty_when_absent() {
        Optional<Member> found = memberRepository.findByEmail("none@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("회원 저장 시 createdAt 자동 설정")
    void save_member_sets_created_at() {
        Member saved = memberRepository.save(createMember("user@example.com", "유저"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
