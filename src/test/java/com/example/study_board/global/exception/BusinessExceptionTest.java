package com.example.study_board.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    @DisplayName("BusinessException은 ErrorCode와 기본 메시지를 보유한다")
    void business_exception_holds_error_code() {
        BusinessException exception = new BusinessException(ErrorCode.BAD_REQUEST);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.BAD_REQUEST.getDefaultMessage());
    }

    @Test
    @DisplayName("BusinessException은 커스텀 메시지를 가질 수 있다")
    void business_exception_with_custom_message() {
        BusinessException exception = new BusinessException(ErrorCode.BAD_REQUEST, "커스텀 메시지");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("커스텀 메시지");
    }

    @Test
    @DisplayName("ResourceNotFoundException은 RESOURCE_NOT_FOUND를 가진 BusinessException이다")
    void resource_not_found_is_business_exception() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Post", 999L);

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Post not found. id=999");
    }

    @Test
    @DisplayName("ForbiddenException은 FORBIDDEN을 가진 BusinessException이다")
    void forbidden_is_business_exception() {
        ForbiddenException exception = new ForbiddenException();

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getDefaultMessage());
    }
}
