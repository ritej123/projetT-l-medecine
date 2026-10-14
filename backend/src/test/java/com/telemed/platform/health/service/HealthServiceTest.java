package com.telemed.platform.health.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthServiceTest {

    private final HealthService healthService = new HealthService();

    @Test
    void returnsUpStatus() {
        assertThat(healthService.getHealth().status()).isEqualTo("UP");
    }
}
