package com.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import com.controlcenter.github.DispatchResult;
import com.controlcenter.github.WorkflowDispatchRequest;
import com.controlcenter.github.WorkflowDispatcher;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class DeploymentServiceTest {

    private static final String SHA = "3f2a9c1e5b7d4f6a8c0e2b4d6f8a0c2e4b6d8f0a";

    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @MockitoBean
    private WorkflowDispatcher dispatcher;

    private Long environmentId;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        Long applicationId = applicationService.register(
                new ApplicationRequest("payments-api", null, "https://github.com/acme/payments-api", "main")).getId();
        environmentId = environmentService.create(applicationId, new EnvironmentRequest("dev", null)).getId();
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.dispatched("dispatched"));
    }

    @Test
    void dispatchesWorkflowAndMarksDeploymentRunning() {
        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(SHA, null, "release"));

        ArgumentCaptor<WorkflowDispatchRequest> request = ArgumentCaptor.forClass(WorkflowDispatchRequest.class);
        verify(dispatcher).dispatch(request.capture());
        assertThat(request.getValue()).isEqualTo(new WorkflowDispatchRequest(
                "https://github.com/acme/payments-api", "main", "dev", SHA, deployment.getId()));
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(deployment.getVersion()).isEqualTo("3f2a9c1e5b7d");
        assertThat(deployment.getStartedAt()).isNotNull();
        assertThat(environmentService.get(environmentId).getStatus()).isEqualTo(EnvironmentStatus.DEPLOYING);
    }

    @Test
    void keepsDeploymentPendingWhenDispatchIsDisabled() {
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.skipped("dispatch disabled"));

        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(SHA, "1.0.0", null));

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(deployment.getMessage()).isEqualTo("dispatch disabled");
    }

    @Test
    void marksDeploymentFailedWhenDispatchFails() {
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.failed("GitHub API returned 404"));

        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(SHA, "1.0.0", null));

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getCompletedAt()).isNotNull();
        assertThat(environmentService.get(environmentId).getStatus()).isEqualTo(EnvironmentStatus.FAILED);
    }

    @Test
    void successfulDeploymentUpdatesCurrentVersion() {
        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(SHA, "1.0.0", null));

        deploymentService.updateStatus(deployment.getId(), DeploymentStatus.SUCCESS, "service stable");

        Environment environment = environmentService.get(environmentId);
        assertThat(environment.getCurrentVersion()).isEqualTo("1.0.0");
        assertThat(environment.getStatus()).isEqualTo(EnvironmentStatus.ACTIVE);
    }

    @Test
    void rejectsConcurrentDeploymentsToTheSameEnvironment() {
        deploymentService.deploy(environmentId, new DeploymentRequest(SHA, "1.0.0", null));

        assertThatThrownBy(() -> deploymentService.deploy(environmentId, new DeploymentRequest("other", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a deployment in progress");
    }

    @Test
    void rejectsMutableLatestTag() {
        assertThatThrownBy(() -> deploymentService.deploy(environmentId, new DeploymentRequest("latest", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest");
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(SHA, "1.0.0", null));
        deploymentService.updateStatus(deployment.getId(), DeploymentStatus.FAILED, "tasks failed to start");

        assertThatThrownBy(() -> deploymentService.updateStatus(deployment.getId(), DeploymentStatus.SUCCESS, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void failsForUnknownEnvironment() {
        assertThatThrownBy(() -> deploymentService.deploy(999_999L, new DeploymentRequest(SHA, null, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
