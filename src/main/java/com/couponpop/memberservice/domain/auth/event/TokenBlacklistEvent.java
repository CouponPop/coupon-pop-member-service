package com.couponpop.memberservice.domain.auth.event;

public record TokenBlacklistEvent(
        String token,
        long expirationMillis
) {

    public static TokenBlacklistEvent of(String token, long expirationMillis) {
        return new TokenBlacklistEvent(token, expirationMillis);
    }
}