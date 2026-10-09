package com.controlcenter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the periodic HTTP health probe of registered environments. Invalid values stop
 * the application at startup instead of producing a busy loop or malformed probe URLs.
 *
 * @param enabled  whether the background scheduler runs
 * @param interval delay between two probe rounds; must be positive
 * @param timeout  connect/read timeout of a single probe; must be positive
 * @param path     path appended to the environment URL; must start with '/'
 */
@ConfigurationProperties(prefix = "control-center.health-check")
public record HealthCheckProperties(boolean enabled, Duration interval, Duration timeout, String path) {

    public HealthCheckProperties {
        interval = interval == null ? Duration.ofSeconds(60) : interval;
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        path = (path == null || path.isBlank()) ? "/api/health" : path.trim();
        requirePositive(interval, "interval (HEALTH_CHECK_INTERVAL)");
        requirePositive(timeout, "timeout (HEALTH_CHECK_TIMEOUT)");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("control-center.health-check.path must start with '/', got '%s'"
                    .formatted(path));
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("control-center.health-check.%s must be positive, got %s"
                    .formatted(name, value));
        }
    }
}
