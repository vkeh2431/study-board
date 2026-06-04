package com.example.study_board.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("RESOURCE_NOT_FOUND는 404 상태와 코드 문자열을 가진다")
    void resource_not_found_mapping() {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("VALIDATION_ERROR는 400 상태와 코드 문자열을 가진다")
    void validation_error_mapping() {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("BAD_REQUEST는 400 상태와 코드 문자열을 가진다")
    void bad_request_mapping() {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("INTERNAL_SERVER_ERROR는 500 상태와 코드 문자열을 가진다")
    void internal_server_error_mapping() {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode.getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("DUPLICATE_EMAIL는 409 상태와 코드 문자열을 가진다")
    void duplicate_email_mapping() {
        ErrorCode errorCode = ErrorCode.DUPLICATE_EMAIL;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode.getCode()).isEqualTo("DUPLICATE_EMAIL");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("INVALID_CREDENTIALS는 401 상태와 코드 문자열을 가진다")
    void invalid_credentials_mapping() {
        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode.getCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("UNAUTHORIZED는 401 상태와 코드 문자열을 가진다")
    void unauthorized_mapping() {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }

    @Test
    @DisplayName("FORBIDDEN는 403 상태와 코드 문자열을 가진다")
    void forbidden_mapping() {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode.getCode()).isEqualTo("FORBIDDEN");
        assertThat(errorCode.getDefaultMessage()).isNotBlank();
    }
}
