package com.controlcenter.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Application;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.service.EnvironmentHealthChecker;
import com.controlcenter.service.EnvironmentOverview;
import com.controlcenter.service.EnvironmentOverviewService;
import com.controlcenter.service.EnvironmentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnvironmentController.class)
class EnvironmentControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EnvironmentService environmentService;
    @MockitoBean
    private EnvironmentHealthChecker healthChecker;
    @MockitoBean
    private EnvironmentOverviewService overviewService;

    @Test
    void reportsDegradedEnvironmentWithFailureDetails() throws Exception {
        Environment prod = environment(5L, "prod");
        Deployment live = deployment(prod, 20L, "1.0.0", DeploymentStatus.SUCCESS, "service stable");
        prod.deploymentSucceeded("1.0.0");
        prod.recordHealth(HealthStatus.HEALTHY, NOW);
        Deployment failed = deployment(prod, 21L, "2.0.0", DeploymentStatus.FAILED, "tasks failed to start");
        when(overviewService.forEnvironment(5L)).thenReturn(EnvironmentOverview.of(prod, failed, live));

        mvc.perform(get("/api/environments/5/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environmentName").value("prod"))
                .andExpect(jsonPath("$.applicationName").value("billing"))
                .andExpect(jsonPath("$.state").value("DEGRADED"))
                .andExpect(jsonPath("$.stateDescription").value(
                        "The latest deployment failed; the previous version is still serving"))
                .andExpect(jsonPath("$.deploymentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.healthStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.liveDeployment.id").value(20))
                .andExpect(jsonPath("$.latestDeployment.id").value(21))
                .andExpect(jsonPath("$.latestDeployment.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("tasks failed to start"));
    }

    @Test
    void reportsEveryEnvironmentOfAnApplication() throws Exception {
        Environment dev = environment(4L, "dev");
        Environment prod = environment(5L, "prod");
        when(overviewService.forApplication(1L)).thenReturn(List.of(
                EnvironmentOverview.of(dev, null, null),
                EnvironmentOverview.of(prod, deployment(prod, 30L, "1.0.0", null, null), null)));

        mvc.perform(get("/api/applications/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].state").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$[0].latestDeployment").doesNotExist())
                .andExpect(jsonPath("$[1].state").value("DEPLOYING"))
                .andExpect(jsonPath("$[1].latestDeployment.status").value("PENDING"))
                .andExpect(jsonPath("$[1].liveDeployment").doesNotExist());
    }

    @Test
    void explainsWhyAnEnvironmentIsDown() throws Exception {
        Environment prod = environment(5L, "prod");
        Deployment live = deployment(prod, 20L, "1.0.0", DeploymentStatus.SUCCESS, null);
        prod.deploymentSucceeded("1.0.0");
        prod.recordHealth(HealthStatus.UNHEALTHY, "No response within 3000 ms", NOW);
        when(overviewService.forEnvironment(5L)).thenReturn(EnvironmentOverview.of(prod, live, live));

        mvc.perform(get("/api/environments/5/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DOWN"))
                .andExpect(jsonPath("$.healthStatus").value("UNHEALTHY"))
                .andExpect(jsonPath("$.healthDetail").value("No response within 3000 ms"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownEnvironment() throws Exception {
        when(overviewService.forEnvironment(99L)).thenThrow(new NotFoundException("Environment", 99L));

        mvc.perform(get("/api/environments/99/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Environment with id 99 was not found"));
    }

    private static Environment environment(Long id, String name) {
        Application application = new Application("billing", null, "https://github.com/acme/billing", "main");
        ReflectionTestUtils.setField(application, "id", 1L);
        Environment environment = new Environment(application, name, null);
        ReflectionTestUtils.setField(environment, "id", id);
        return environment;
    }

    /** A deployment in the given status; a null status keeps it PENDING. */
    private static Deployment deployment(Environment environment, Long id, String version, DeploymentStatus status,
                                         String message) {
        Deployment deployment = new Deployment(environment, version, "sha-" + version, false, null);
        ReflectionTestUtils.setField(deployment, "id", id);
        if (status != null) {
            deployment.transitionTo(status, message, NOW);
        }
        return deployment;
    }
}
