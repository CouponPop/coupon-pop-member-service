package com.couponpop.memberservice.global.feign.fcmtoken;

import com.couponpop.couponpopcoremodule.dto.fcmtoken.request.FcmTokenExpireRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "${client.notification-service.name}", url = "${client.notification-service.url}")
public interface FcmTokenFeignClient {

    @PostMapping("/v1/fcm-token/expire")
    void expireFcmToken(FcmTokenExpireRequest fcmTokenExpireRequest);
}
