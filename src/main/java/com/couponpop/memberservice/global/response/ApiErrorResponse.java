package com.couponpop.memberservice.global.response;

import com.couponpop.memberservice.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiErrorResponse {

    private final ApiErrorBody error;

    public static ApiErrorResponse from(HttpStatus httpStatus, String message, HttpServletRequest request) {
        return new ApiErrorResponse(
                buildApiErrorBody(httpStatus, httpStatus.name(), message, request)
        );
    }

    public static ApiErrorResponse from(ErrorCode errorCode, HttpServletRequest request) {
        return new ApiErrorResponse(
                buildApiErrorBody(errorCode.getHttpStatus(), errorCode.name(), errorCode.getMessage(), request)
        );
    }

    private static ApiErrorBody buildApiErrorBody(HttpStatus httpStatus, String errorCodeName, String message, HttpServletRequest request) {
        return ApiErrorBody.builder()
                .status(httpStatus.toString())
                .code(errorCodeName)
                .message(message)
                .requestUrl(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Builder
    private record ApiErrorBody(String status, String code, String message, String requestUrl, LocalDateTime timestamp) {
    }
}
