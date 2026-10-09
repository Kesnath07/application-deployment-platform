package com.controlcenter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final Environment environment =
            new Environment(new Application("api", null, "https://github.com/acme/api", "main"), "prod", null);

    @Test
    void keepsTheReasonOfAFailedProbe() {
        environment.recordHealth(HealthStatus.UNHEALTHY, "HTTP 502 Bad Gateway", NOW);

        assertThat(environment.getHealthStatus()).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(environment.getHealthDetail()).isEqualTo("HTTP 502 Bad Gateway");
        assertThat(environment.getLastHealthCheckAt()).isEqualTo(NOW);
    }

    @Test
    void dropsTheDetailWhenTheProbeSucceeds() {
        environment.recordHealth(HealthStatus.UNHEALTHY, "Connection refused", NOW);

        environment.recordHealth(HealthStatus.HEALTHY, "ignored", NOW.plusSeconds(60));

        assertThat(environment.getHealthDetail()).isNull();
    }

    @Test
    void capsLongDetailsToTheColumnSize() {
        environment.recordHealth(HealthStatus.UNHEALTHY, "x".repeat(400), NOW);

        assertThat(environment.getHealthDetail()).hasSize(Environment.HEALTH_DETAIL_LIMIT).endsWith("…");
    }
}
