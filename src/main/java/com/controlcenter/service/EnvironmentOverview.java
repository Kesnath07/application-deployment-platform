package com.controlcenter.service;

import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.OperationalState;

/**
 * An environment together with its most recent and its live deployment.
 *
 * @param latestDeployment most recent deployment of any status; null when nothing was deployed yet
 * @param liveDeployment   successful deployment currently serving traffic; null when no version is live
 * @param failureReason    last pipeline note of the latest deployment when it failed, otherwise null
 */
public record EnvironmentOverview(Environment environment, Deployment latestDeployment, Deployment liveDeployment,
                                  OperationalState state, String failureReason) {

    public static EnvironmentOverview of(Environment environment, Deployment latestDeployment,
                                         Deployment liveDeployment) {
        String failureReason = latestDeployment != null && latestDeployment.getStatus() == DeploymentStatus.FAILED
                ? latestDeployment.getLatestNote() : null;
        return new EnvironmentOverview(environment, latestDeployment, liveDeployment,
                OperationalState.of(environment, latestDeployment), failureReason);
    }

    public boolean isDeploymentInProgress() {
        return latestDeployment != null && latestDeployment.isInProgress();
    }

    /** Why the environment needs attention (failed probe or failed deployment); null when it does not. */
    public String getAttentionReason() {
        return switch (state) {
            case DOWN -> environment.getHealthDetail() == null
                    ? "Health check failing" : "Health check failing: " + environment.getHealthDetail();
            case FAILED, DEGRADED -> failureReason == null ? "Latest deployment failed" : failureReason;
            default -> null;
        };
    }
}
