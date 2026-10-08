package com.controlcenter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the periodic HTTP health probe of registered environments.
 *
 * @param enabled  whether the background scheduler runs
 * @param interval delay between two probe rounds
 * @param timeout  connect/read timeout of a single probe
 * @param path     path appended to the environment URL
 */
@ConfigurationProperties(prefix = "control-center.health-check")
public record HealthCheckProperties(boolean enabled, Duration interval, Duration timeout, String path) {

    public HealthCheckProperties {
        interval = interval == null ? Duration.ofSeconds(60) : interval;
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        path = (path == null || path.isBlank()) ? "/api/health" : path;
    }
}
