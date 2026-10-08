package com.controlcenter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.controlcenter.common.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeploymentTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T10:05:00Z");

    private final Environment environment =
            new Environment(new Application("api", null, "https://github.com/acme/api", "main"), "dev", null);

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING, RUNNING, true",
            "PENDING, SUCCESS, true",
            "PENDING, FAILED, true",
            "PENDING, ROLLED_BACK, false",
            "RUNNING, SUCCESS, true",
            "RUNNING, FAILED, true",
            "RUNNING, PENDING, false",
            "SUCCESS, ROLLED_BACK, true",
            "SUCCESS, FAILED, false",
            "FAILED, RUNNING, false",
            "ROLLED_BACK, SUCCESS, false"
    })
    void enforcesLifecycleTransitions(DeploymentStatus from, DeploymentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void tracksTimestampsAcrossTheLifecycle() {
        Deployment deployment = new Deployment(environment, "1.0.0", "abc123", false, "release");

        deployment.transitionTo(DeploymentStatus.RUNNING, "started", T1);
        deployment.transitionTo(DeploymentStatus.SUCCESS, "done", T2);

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(deployment.getStartedAt()).isEqualTo(T1);
        assertThat(deployment.getCompletedAt()).isEqualTo(T2);
        assertThat(deployment.getMessage()).isEqualTo("release\nstarted\ndone");
    }

    @Test
    void rejectsIllegalTransitions() {
        Deployment deployment = new Deployment(environment, "1.0.0", "abc123", false, null);
        deployment.transitionTo(DeploymentStatus.FAILED, null, T1);

        assertThatThrownBy(() -> deployment.transitionTo(DeploymentStatus.SUCCESS, null, T2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FAILED to SUCCESS");
    }

    @Test
    void keepsMostRecentMessagesWithinColumnLimit() {
        Deployment deployment = new Deployment(environment, "1.0.0", "abc123", false, "x".repeat(990));

        deployment.appendMessage("latest note");

        assertThat(deployment.getMessage()).hasSize(1000).endsWith("latest note");
    }

    @Test
    void failedDeploymentKeepsEnvironmentActiveWhenAVersionIsLive() {
        environment.deploymentSucceeded("1.0.0");
        environment.deploymentStarted();
        environment.deploymentFailed();

        assertThat(environment.getStatus()).isEqualTo(EnvironmentStatus.ACTIVE);
        assertThat(environment.getCurrentVersion()).isEqualTo("1.0.0");
    }

    @Test
    void failedFirstDeploymentMarksEnvironmentFailed() {
        environment.deploymentStarted();
        environment.deploymentFailed();

        assertThat(environment.getStatus()).isEqualTo(EnvironmentStatus.FAILED);
    }
}
