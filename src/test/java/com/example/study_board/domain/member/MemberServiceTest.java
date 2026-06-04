package com.example.study_board.domain.member;

import com.example.study_board.dto.member.MemberResponse;
import com.example.study_board.dto.member.SignupRequest;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("회원가입 시 비밀번호를 암호화하여 저장한다")
    void signup_encodes_password() {
        SignupRequest request = new SignupRequest("user@example.com", "유저", "raw-password");
        given(memberRepository.existsByEmail("user@example.com")).willReturn(false);
        given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");
        given(memberRepository.save(org.mockito.ArgumentMatchers.any(Member.class)))
                .willAnswer(inv -> inv.getArgument(0));

        memberService.signup(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member persisted = captor.getValue();
        assertThat(persisted.getPassword()).isEqualTo("encoded-password");
        assertThat(persisted.getPassword()).isNotEqualTo("raw-password");
        assertThat(persisted.getRole()).isEqualTo(Role.USER);
        verify(passwordEncoder).encode("raw-password");
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 DUPLICATE_EMAIL 예외")
    void signup_duplicate_email_throws() {
        SignupRequest request = new SignupRequest("user@example.com", "유저", "raw-password");
        given(memberRepository.existsByEmail("user@example.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("회원가입 성공 시 MemberResponse를 반환한다")
    void signup_returns_member_response() {
        SignupRequest request = new SignupRequest("user@example.com", "유저", "raw-password");
        given(memberRepository.existsByEmail("user@example.com")).willReturn(false);
        given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");
        given(memberRepository.save(org.mockito.ArgumentMatchers.any(Member.class)))
                .willAnswer(inv -> inv.getArgument(0));

        MemberResponse response = memberService.signup(request);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.username()).isEqualTo("유저");
        assertThat(response.role()).isEqualTo(Role.USER);
    }
}
