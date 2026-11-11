package com.telemed.platform.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RequiredSecretsValidatorTest {

    @Test
    void refusesToStartWithoutDatabasePassword() {
        assertThatThrownBy(() -> new RequiredSecretsValidator(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void acceptsConfiguredDatabasePassword() {
        new RequiredSecretsValidator("configured-database-password");
    }
}
