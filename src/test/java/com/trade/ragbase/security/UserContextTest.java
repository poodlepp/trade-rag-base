package com.trade.ragbase.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void usesDefaultLearningUserWhenNotSet() {
        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getDepartmentId()).isEqualTo("default");
        assertThat(UserContext.getRole()).isEqualTo("admin");
        assertThat(UserContext.isAdmin()).isTrue();
    }

    @Test
    void storesAndClearsThreadLocalUser() {
        UserContext.set(9L, "TECH", "USER");

        assertThat(UserContext.getUserId()).isEqualTo(9L);
        assertThat(UserContext.getDepartmentId()).isEqualTo("TECH");
        assertThat(UserContext.getRole()).isEqualTo("USER");
        assertThat(UserContext.isAdmin()).isFalse();

        UserContext.clear();

        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getDepartmentId()).isEqualTo("default");
        assertThat(UserContext.getRole()).isEqualTo("admin");
    }
}
