package com.couponpop.memberservice.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(

        @NotBlank
        String fcmToken
) {
}
