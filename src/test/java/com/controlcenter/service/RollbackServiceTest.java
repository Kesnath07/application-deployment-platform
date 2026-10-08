package com.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.common.ConflictException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.github.DispatchResult;
import com.controlcenter.github.WorkflowDispatcher;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class RollbackServiceTest {

    @Autowired
    private RollbackService rollbackService;
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
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.dispatched("dispatched"));
        Long applicationId = applicationService.register(
                new ApplicationRequest("orders-api", null, "https://github.com/acme/orders-api", "main")).getId();
        environmentId = environmentService.create(applicationId, new EnvironmentRequest("prod", null)).getId();
    }

    @Test
    void rollsBackToPreviousSuccessfulVersion() {
        Deployment v1 = deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        Deployment v2 = deploySuccessfully("bbbbbbbbbbbb", "2.0.0");

        Deployment rollback = rollbackService.rollback(environmentId, null, "error rate spike");

        assertThat(rollback.isRollback()).isTrue();
        assertThat(rollback.getImageTag()).isEqualTo(v1.getImageTag());
        assertThat(rollback.getVersion()).isEqualTo("1.0.0");
        assertThat(rollback.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(rollback.getMessage()).contains("Rollback from 2.0.0 to 1.0.0").contains("error rate spike");
        // Initial v1 deployment + the rollback both dispatch the same immutable image.
        verify(dispatcher, times(2)).dispatch(argThat(request -> request.imageTag().equals("aaaaaaaaaaaa")));
        // The live deployment is only flagged once the rollback has actually succeeded.
        assertThat(deploymentService.get(v2.getId()).getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
    }

    @Test
    void marksReplacedDeploymentRolledBackWhenRollbackSucceeds() {
        deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        Deployment v2 = deploySuccessfully("bbbbbbbbbbbb", "2.0.0");

        Deployment rollback = rollbackService.rollback(environmentId, null, null);
        deploymentService.updateStatus(rollback.getId(), DeploymentStatus.SUCCESS, "service stable");

        assertThat(deploymentService.get(v2.getId()).getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
        assertThat(environmentService.get(environmentId).getCurrentVersion()).isEqualTo("1.0.0");
    }

    @Test
    void failedRollbackLeavesLiveDeploymentUntouched() {
        deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        Deployment v2 = deploySuccessfully("bbbbbbbbbbbb", "2.0.0");

        Deployment rollback = rollbackService.rollback(environmentId, null, null);
        deploymentService.updateStatus(rollback.getId(), DeploymentStatus.FAILED, "tasks unhealthy");

        assertThat(deploymentService.get(v2.getId()).getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(environmentService.get(environmentId).getCurrentVersion()).isEqualTo("2.0.0");
    }

    @Test
    void rollsBackToExplicitTarget() {
        Deployment v1 = deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        deploySuccessfully("bbbbbbbbbbbb", "2.0.0");
        deploySuccessfully("cccccccccccc", "3.0.0");

        Deployment rollback = rollbackService.rollback(environmentId, v1.getId(), null);

        assertThat(rollback.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void rejectsTargetWithTheLiveImage() {
        deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        Deployment live = deploySuccessfully("bbbbbbbbbbbb", "2.0.0");

        assertThatThrownBy(() -> rollbackService.rollback(environmentId, live.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not a valid rollback target");
    }

    @Test
    void rejectsRollbackWithoutHistory() {
        assertThatThrownBy(() -> rollbackService.rollback(environmentId, null, null))
                .isInstanceOf(ConflictException.class);

        deploySuccessfully("aaaaaaaaaaaa", "1.0.0");

        assertThatThrownBy(() -> rollbackService.rollback(environmentId, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No previous successful version");
    }

    @Test
    void listsRollbackCandidatesNewestFirst() {
        Deployment v1 = deploySuccessfully("aaaaaaaaaaaa", "1.0.0");
        Deployment v2 = deploySuccessfully("bbbbbbbbbbbb", "2.0.0");
        deploySuccessfully("cccccccccccc", "3.0.0");

        assertThat(rollbackService.rollbackCandidates(environmentId))
                .extracting(Deployment::getId)
                .containsExactly(v2.getId(), v1.getId());
    }

    private Deployment deploySuccessfully(String imageTag, String version) {
        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(imageTag, version, null));
        return deploymentService.updateStatus(deployment.getId(), DeploymentStatus.SUCCESS, "service stable");
    }
}
