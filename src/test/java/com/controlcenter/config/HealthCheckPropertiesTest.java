package com.controlcenter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class HealthCheckPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void appliesDefaults() {
        HealthCheckProperties properties = new HealthCheckProperties(true, null, null, " ");

        assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.path()).isEqualTo("/api/health");
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThatThrownBy(() -> new HealthCheckProperties(true, Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interval (HEALTH_CHECK_INTERVAL) must be positive");
        assertThatThrownBy(() -> new HealthCheckProperties(true, null, Duration.ofSeconds(-1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout (HEALTH_CHECK_TIMEOUT) must be positive");
    }

    @Test
    void bindsEnvironmentStyleValues() {
        context.withPropertyValues("control-center.health-check.interval=PT30S",
                        "control-center.health-check.timeout=PT1.5S")
                .run(ctx -> {
                    HealthCheckProperties properties = ctx.getBean(HealthCheckProperties.class);
                    assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.timeout()).isEqualTo(Duration.ofMillis(1500));
                });
    }

    @Test
    void stopsStartupWhenThePathIsNotAbsolute() {
        context.withPropertyValues("control-center.health-check.path=api/health")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause()
                        .hasMessage("control-center.health-check.path must start with '/', got 'api/health'"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HealthCheckProperties.class)
    static class PropertiesConfig {
    }
}
