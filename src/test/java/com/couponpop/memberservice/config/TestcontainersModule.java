package com.couponpop.memberservice.config;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class TestcontainersModule {

    // Redis 컨테이너를 정적으로 선언하고, JUnit LifeCycle에 종속되지 않도록 관리합니다.
    public static final GenericContainer<?> REDIS_CONTAINER;

    static {
        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:6-alpine"))
                .withExposedPorts(6379)
                .withReuse(true); // 컨테이너 재사용 활성화
        REDIS_CONTAINER.start();
    }
}