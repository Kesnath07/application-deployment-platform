package com.controlcenter.api.dto;

import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import java.time.Instant;

public record DeploymentResponse(Long id, Long applicationId, String applicationName, Long environmentId,
                                 String environmentName, String version, String imageTag, DeploymentStatus status,
                                 boolean rollback, String message, Instant createdAt, Instant startedAt,
                                 Instant completedAt) {

    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(deployment.getId(),
                deployment.getApplication().getId(), deployment.getApplication().getName(),
                deployment.getEnvironment().getId(), deployment.getEnvironment().getName(),
                deployment.getVersion(), deployment.getImageTag(), deployment.getStatus(), deployment.isRollback(),
                deployment.getMessage(), deployment.getCreatedAt(), deployment.getStartedAt(),
                deployment.getCompletedAt());
    }
}
