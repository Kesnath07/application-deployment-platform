package com.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.OperationalState;
import com.controlcenter.github.DispatchResult;
import com.controlcenter.github.WorkflowDispatcher;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class EnvironmentOverviewServiceTest {

    @Autowired
    private EnvironmentOverviewService overviewService;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private RollbackService rollbackService;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @MockitoBean
    private WorkflowDispatcher dispatcher;

    private Long applicationId;
    private Long devId;
    private Long prodId;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.dispatched("dispatched"));
        applicationId = applicationService.register(
                new ApplicationRequest("search-api", null, "https://github.com/acme/search-api", "main")).getId();
        devId = environmentService.create(applicationId, new EnvironmentRequest("dev", null)).getId();
        prodId = environmentService.create(applicationId, new EnvironmentRequest("prod", null)).getId();
    }

    @Test
    void reportsLatestAndLiveDeploymentWithTheFailureReason() {
        Deployment live = deploy(prodId, "aaaaaaaaaaaa", DeploymentStatus.SUCCESS, "service stable");
        Deployment failed = deploy(prodId, "bbbbbbbbbbbb", DeploymentStatus.FAILED, "tasks failed to start");

        EnvironmentOverview overview = overviewService.forEnvironment(prodId);

        assertThat(overview.state()).isEqualTo(OperationalState.DEGRADED);
        assertThat(overview.latestDeployment().getId()).isEqualTo(failed.getId());
        assertThat(overview.liveDeployment().getId()).isEqualTo(live.getId());
        assertThat(overview.failureReason()).isEqualTo("tasks failed to start");
        assertThat(overview.isDeploymentInProgress()).isFalse();
    }

    @Test
    void tracksEachEnvironmentOfAnApplicationSeparately() {
        deploy(devId, "aaaaaaaaaaaa", DeploymentStatus.SUCCESS, null);
        Deployment running = deploymentService.deploy(prodId, new DeploymentRequest("aaaaaaaaaaaa", null, null));

        List<EnvironmentOverview> overviews = overviewService.forApplication(applicationId);

        assertThat(overviews).extracting(overview -> overview.environment().getName()).containsExactly("dev", "prod");
        EnvironmentOverview dev = overviews.get(0);
        EnvironmentOverview prod = overviews.get(1);
        assertThat(dev.state()).isEqualTo(OperationalState.UNVERIFIED);
        assertThat(dev.failureReason()).isNull();
        assertThat(prod.state()).isEqualTo(OperationalState.DEPLOYING);
        assertThat(prod.isDeploymentInProgress()).isTrue();
        assertThat(prod.latestDeployment().getId()).isEqualTo(running.getId());
        assertThat(prod.liveDeployment()).isNull();
    }

    @Test
    void liveDeploymentFollowsASuccessfulRollback() {
        deploy(prodId, "aaaaaaaaaaaa", DeploymentStatus.SUCCESS, null);
        deploy(prodId, "bbbbbbbbbbbb", DeploymentStatus.SUCCESS, null);
        Deployment rollback = rollbackService.rollback(prodId, null, null);
        deploymentService.updateStatus(rollback.getId(), DeploymentStatus.SUCCESS, null);

        EnvironmentOverview overview = overviewService.forEnvironment(prodId);

        assertThat(overview.liveDeployment().getId()).isEqualTo(rollback.getId());
        assertThat(overview.latestDeployment().getId()).isEqualTo(rollback.getId());
        assertThat(overview.environment().getCurrentVersion()).isEqualTo("aaaaaaaaaaaa");
    }

    @Test
    void reportsEnvironmentsWithoutDeploymentsAsNotDeployed() {
        assertThat(overviewService.all())
                .hasSize(2)
                .allSatisfy(overview -> {
                    assertThat(overview.state()).isEqualTo(OperationalState.NOT_DEPLOYED);
                    assertThat(overview.latestDeployment()).isNull();
                    assertThat(overview.liveDeployment()).isNull();
                });
    }

    @Test
    void failsForUnknownEnvironmentOrApplication() {
        assertThatThrownBy(() -> overviewService.forEnvironment(999_999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> overviewService.forApplication(999_999L)).isInstanceOf(NotFoundException.class);
    }

    private Deployment deploy(Long environmentId, String imageTag, DeploymentStatus outcome, String message) {
        Deployment deployment = deploymentService.deploy(environmentId, new DeploymentRequest(imageTag, null, null));
        return deploymentService.updateStatus(deployment.getId(), outcome, message);
    }
}
