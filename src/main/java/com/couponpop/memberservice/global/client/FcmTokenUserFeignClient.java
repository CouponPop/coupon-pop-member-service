package com.couponpop.memberservice.global.client;

import com.couponpop.couponpopcoremodule.dto.fcmtoken.request.FcmTokenExpireRequest;
import com.couponpop.memberservice.global.config.UserFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "${client.notification-service.name}", url = "${client.notification-service.url}", configuration = UserFeignConfig.class)
public interface FcmTokenUserFeignClient {

    @PostMapping("/internal/v1/fcm-token/expire")
    void expireFcmToken(FcmTokenExpireRequest fcmTokenExpireRequest);
}
