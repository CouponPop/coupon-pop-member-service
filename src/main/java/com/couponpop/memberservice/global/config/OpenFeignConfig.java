package com.couponpop.memberservice.global.config;

import com.couponpop.security.constants.SecurityTemplates;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Configuration
public class OpenFeignConfig {

    /**
     * Feign Client 요청 시 JWT 토큰을 자동으로 헤더에 추가하는 인터셉터
     *
     * @return RequestInterceptor JWT 토큰을 포함하는 인터셉터
     */
    @Bean
    public RequestInterceptor jwtRequestInterceptor() {

        return requestTemplate -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs == null) {
                return;
            }

            HttpServletRequest request = attrs.getRequest();
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authHeader) && authHeader.startsWith(SecurityTemplates.BEARER_TOKEN_PREFIX)) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, authHeader);
            }
        };
    }
}
