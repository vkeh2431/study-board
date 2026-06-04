package com.example.study_board.global.config;

import com.example.study_board.global.security.CustomUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA Auditing(@CreatedBy/@LastModifiedBy)이 사용할 현재 사용자 식별자 제공자.
 * SecurityContext의 principal({@link CustomUserDetails})에서 memberId를 읽는다.
 *
 * <p>미인증/익명/principal이 {@link CustomUserDetails}가 아닌 경우(예: 로그인 진행 중 principal=String,
 * SecurityContext가 없는 {@code @DataJpaTest}, 회원가입 등 인증 전 컨텍스트) {@link Optional#empty()}를
 * 반환한다. 이 경우 감사 컬럼은 NULL로 남으므로 {@code created_by}/{@code last_modified_by}는 nullable이어야 한다.
 */
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.getMemberId());
    }
}
