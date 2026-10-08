package com.controlcenter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OperationalStateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final Environment environment =
            new Environment(new Application("api", null, "https://github.com/acme/api", "main"), "prod", null);

    @Test
    void notDeployedWithoutAnyDeployment() {
        assertThat(OperationalState.of(environment, null)).isEqualTo(OperationalState.NOT_DEPLOYED);
    }

    @Test
    void deployingWhileTheLatestDeploymentIsInProgress() {
        assertThat(OperationalState.of(environment, deployment(null))).isEqualTo(OperationalState.DEPLOYING);
        assertThat(OperationalState.of(environment, deployment(DeploymentStatus.RUNNING)))
                .isEqualTo(OperationalState.DEPLOYING);
    }

    @Test
    void failedWhenTheFirstDeploymentFailed() {
        assertThat(OperationalState.of(environment, deployment(DeploymentStatus.FAILED)))
                .isEqualTo(OperationalState.FAILED);
    }

    @Test
    void unverifiedWhenLiveButNeverProbed() {
        environment.deploymentSucceeded("1.0.0");

        assertThat(OperationalState.of(environment, deployment(DeploymentStatus.SUCCESS)))
                .isEqualTo(OperationalState.UNVERIFIED);
    }

    @Test
    void healthyWhenLiveAndTheProbePasses() {
        environment.deploymentSucceeded("1.0.0");
        environment.recordHealth(HealthStatus.HEALTHY, NOW);

        assertThat(OperationalState.of(environment, deployment(DeploymentStatus.SUCCESS)))
                .isEqualTo(OperationalState.HEALTHY);
    }

    @Test
    void degradedWhenTheLatestDeploymentFailedButAVersionIsStillLive() {
        environment.deploymentSucceeded("1.0.0");
        environment.recordHealth(HealthStatus.HEALTHY, NOW);

        OperationalState state = OperationalState.of(environment, deployment(DeploymentStatus.FAILED));

        assertThat(state).isEqualTo(OperationalState.DEGRADED);
        assertThat(state.needsAttention()).isTrue();
    }

    @Test
    void downTakesPrecedenceOverAnInProgressDeployment() {
        environment.deploymentSucceeded("1.0.0");
        environment.recordHealth(HealthStatus.UNHEALTHY, NOW);

        assertThat(OperationalState.of(environment, deployment(DeploymentStatus.RUNNING)))
                .isEqualTo(OperationalState.DOWN);
    }

    @Test
    void failingProbeWithoutALiveVersionIsNotReportedAsDown() {
        environment.recordHealth(HealthStatus.UNHEALTHY, NOW);

        assertThat(OperationalState.of(environment, null)).isEqualTo(OperationalState.NOT_DEPLOYED);
    }

    @Test
    void onlyProblemStatesNeedAttention() {
        assertThat(OperationalState.values())
                .filteredOn(OperationalState::needsAttention)
                .containsExactlyInAnyOrder(OperationalState.FAILED, OperationalState.DOWN, OperationalState.DEGRADED);
    }

    /** A deployment of this environment in the given status; null keeps it PENDING. */
    private Deployment deployment(DeploymentStatus status) {
        Deployment deployment = new Deployment(environment, "2.0.0", "bbbbbbbbbbbb", false, null);
        if (status != null) {
            deployment.transitionTo(status, null, NOW);
        }
        return deployment;
    }
}
