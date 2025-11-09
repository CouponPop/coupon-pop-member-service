package com.couponpop.memberservice.common.client;

import com.couponpop.couponpopcoremodule.dto.fcmtoken.request.FcmTokenExpireRequest;
import com.couponpop.memberservice.common.config.UserFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "${client.notification-service.name}", url = "${client.notification-service.url}", configuration = UserFeignConfig.class)
public interface FcmTokenUserFeignClient {

    @PostMapping("/internal/v1/fcm-token/expire")
    void expireFcmToken(FcmTokenExpireRequest fcmTokenExpireRequest);
}
