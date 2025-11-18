package com.couponpop.memberservice.domain.auth.dto.response;

public record LoginResponse(
        String accessToken
) {
    public static LoginResponse from(String token) {
        return new LoginResponse(token);
    }
}
