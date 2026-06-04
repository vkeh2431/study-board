package com.example.study_board.global.exception;

/**
 * 소유권/권한이 없는 사용자가 리소스를 수정·삭제하려 할 때 던진다(Phase 12).
 * {@link GlobalExceptionHandler}가 {@link BusinessException}을 다형 처리하므로
 * 별도 핸들러 없이 403 + {@code code:"FORBIDDEN"} JSON으로 매핑된다.
 *
 * <p>학습 노트
 * <ul>
 *   <li><b>도메인 권한 검증(현재 방식) vs {@code @PreAuthorize}</b>: 본 프로젝트는 서비스 레이어에서
 *       "현재 사용자 == 작성자(또는 ADMIN)"를 수동 검증한다. 소유권은 엔티티를 조회해야 알 수 있어
 *       메서드 진입 전 평가되는 {@code @PreAuthorize}만으로는 표현이 까다롭고(별도 인가 빈/SpEL 필요),
 *       도메인 규칙을 서비스 안에 두는 편이 테스트·추적이 쉽다. {@code @PreAuthorize}는 역할(role)
 *       단위의 단순 인가에 적합하다.</li>
 *   <li><b>403 vs 404 정책</b>: 리소스는 존재하지만 권한이 없으면 403을 반환한다(현재 방식).
 *       리소스 존재 자체를 숨겨야 하는 보안 요구가 있으면 404로 응답해 존재 노출을 회피하기도 한다.</li>
 * </ul>
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }
}
