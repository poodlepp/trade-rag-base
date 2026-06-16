package com.trade.ragbase.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ServiceConstructorInjectionTest {

    @Test
    void servicesWithTestConstructorsDeclareSpringAutowiredConstructor() {
        assertThat(hasAutowiredConstructor(IndexService.class)).isTrue();
        assertThat(hasAutowiredConstructor(ChunkService.class)).isTrue();
        assertThat(hasAutowiredConstructor(EmbeddingService.class)).isTrue();
    }

    private static boolean hasAutowiredConstructor(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .anyMatch(ServiceConstructorInjectionTest::isAutowired);
    }

    private static boolean isAutowired(Constructor<?> constructor) {
        return constructor.isAnnotationPresent(Autowired.class);
    }
}
