package com.couponpop.memberservice;

import com.couponpop.memberservice.config.TestcontainersModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers // 관례상 유지
class MemberServiceApplicationTests {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        // TestcontainersModule에서 정의된 싱글톤 컨테이너 사용
        registry.add("spring.data.redis.host", TestcontainersModule.REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> TestcontainersModule.REDIS_CONTAINER.getMappedPort(6379).toString());
    }

    @Test
    void contextLoads() {
    }

}
