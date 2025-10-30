package com.couponpop.memberservice.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Map;

@Slf4j
public class TestUtils {

    public static <T> T createEntity(Class<T> clazz, Map<String, Object> fieldValues) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();

            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                try {
                    ReflectionTestUtils.setField(instance, entry.getKey(), entry.getValue());
                } catch (IllegalArgumentException e) {
                    log.info("Warning: Field '{}' not found in class {}", entry.getKey(), clazz.getName());
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("객체 생성 실패", e);
        }
    }
}