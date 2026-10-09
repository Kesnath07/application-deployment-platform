package com.controlcenter.api.dto;

import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.domain.OperationalState;
import com.controlcenter.service.EnvironmentOverview;
import java.time.Instant;

/**
 * Operational status of one environment: the derived state, the raw deployment and health
 * status it is based on, and the deployments that explain it. {@code healthDetail} says why the
 * last health probe failed and {@code failureReason} why the latest deployment failed.
 */
public record EnvironmentStatusResponse(Long environmentId, String environmentName, Long applicationId,
                                        String applicationName, OperationalState state, String stateDescription,
                                        EnvironmentStatus deploymentStatus, HealthStatus healthStatus,
                                        Instant lastHealthCheckAt, String healthDetail, String currentVersion,
                                        DeploymentSummary liveDeployment, DeploymentSummary latestDeployment,
                                        String failureReason) {

    public static EnvironmentStatusResponse from(EnvironmentOverview overview) {
        Environment environment = overview.environment();
        return new EnvironmentStatusResponse(environment.getId(), environment.getName(),
                environment.getApplication().getId(), environment.getApplication().getName(),
                overview.state(), overview.state().getDescription(), environment.getStatus(),
                environment.getHealthStatus(), environment.getLastHealthCheckAt(), environment.getHealthDetail(),
                environment.getCurrentVersion(),
                DeploymentSummary.from(overview.liveDeployment()), DeploymentSummary.from(overview.latestDeployment()),
                overview.failureReason());
    }

    public record DeploymentSummary(Long id, String version, String imageTag, DeploymentStatus status,
                                    boolean rollback, Instant createdAt, Instant startedAt, Instant completedAt) {

        static DeploymentSummary from(Deployment deployment) {
            if (deployment == null) {
                return null;
            }
            return new DeploymentSummary(deployment.getId(), deployment.getVersion(), deployment.getImageTag(),
                    deployment.getStatus(), deployment.isRollback(), deployment.getCreatedAt(),
                    deployment.getStartedAt(), deployment.getCompletedAt());
        }
    }
}
