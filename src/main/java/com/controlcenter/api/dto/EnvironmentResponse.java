package com.controlcenter.api.dto;

import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import com.controlcenter.domain.HealthStatus;
import java.time.Instant;

public record EnvironmentResponse(Long id, Long applicationId, String name, String url, String currentVersion,
                                  EnvironmentStatus status, HealthStatus healthStatus, Instant lastHealthCheckAt,
                                  Instant createdAt, Instant updatedAt) {

    public static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(environment.getId(), environment.getApplication().getId(),
                environment.getName(), environment.getUrl(), environment.getCurrentVersion(),
                environment.getStatus(), environment.getHealthStatus(), environment.getLastHealthCheckAt(),
                environment.getCreatedAt(), environment.getUpdatedAt());
    }
}
