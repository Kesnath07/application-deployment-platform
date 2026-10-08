package com.controlcenter.domain;

/**
 * At-a-glance state of an environment that combines its deployment state with the result of
 * the latest health probe. It is derived on read and never stored.
 */
public enum OperationalState {
    NOT_DEPLOYED("Nothing has been deployed yet"),
    DEPLOYING("A deployment is in progress"),
    FAILED("No version is live because the first deployment failed"),
    DOWN("A version is live but its health check is failing"),
    DEGRADED("The latest deployment failed; the previous version is still serving"),
    HEALTHY("The live version passed its last health check"),
    UNVERIFIED("A version is live but has not passed a health check yet");

    private final String description;

    OperationalState(String description) {
        this.description = description;
    }

    /**
     * Derives the state from the environment and its most recent deployment. A failing health
     * probe of a live version takes precedence, because users are affected right now.
     */
    public static OperationalState of(Environment environment, Deployment latestDeployment) {
        boolean live = environment.getCurrentVersion() != null;
        if (live && environment.getHealthStatus() == HealthStatus.UNHEALTHY) {
            return DOWN;
        }
        if (latestDeployment != null && latestDeployment.isInProgress()) {
            return DEPLOYING;
        }
        boolean latestFailed = latestDeployment != null && latestDeployment.getStatus() == DeploymentStatus.FAILED;
        if (!live) {
            return latestFailed ? FAILED : NOT_DEPLOYED;
        }
        if (latestFailed) {
            return DEGRADED;
        }
        return environment.getHealthStatus() == HealthStatus.HEALTHY ? HEALTHY : UNVERIFIED;
    }

    public boolean needsAttention() {
        return this == FAILED || this == DOWN || this == DEGRADED;
    }

    public String getDescription() {
        return description;
    }
}
