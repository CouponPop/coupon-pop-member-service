package com.couponpop.memberservice.domain.member.exception;

import com.couponpop.memberservice.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    EMAIL_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 사용중인 이메일입니다."),
    PASSWORD_INPUT_INCOMPLETE(HttpStatus.BAD_REQUEST, "비밀번호 변경은 비밀번호, 비밀번호 확인 모두 입력해야 합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
