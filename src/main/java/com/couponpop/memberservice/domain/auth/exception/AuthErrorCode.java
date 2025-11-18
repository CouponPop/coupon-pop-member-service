package com.couponpop.memberservice.domain.auth.exception;


import com.couponpop.memberservice.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // TODO: Security의 ErrorCode와 통합 검토 필요, MemberErrorCode와도 중복 검토 필요

    PASSWORDS_NOT_MATCH(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다. 다시 로그인하세요"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다. 다시 로그인하세요."),
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "잘못된 이메일 또는 비밀번호입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
