package com.couponpop.memberservice.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(

        @NotBlank // App만 존재하여 인증된 사용자는 모두 FCM 토큰을 보낼 것이라 가정
        String fcmToken
) {
}
