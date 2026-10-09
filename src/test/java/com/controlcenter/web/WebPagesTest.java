package com.controlcenter.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.service.ApplicationService;
import com.controlcenter.service.DeploymentService;
import com.controlcenter.service.EnvironmentService;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Renders every server-side page against real data to catch template errors. */
@IntegrationTest
@AutoConfigureMockMvc
class WebPagesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Long applicationId;
    private Long environmentId;
    private Long deploymentId;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        applicationId = applicationService.register(
                new ApplicationRequest("storefront", "Customer web shop", "https://github.com/acme/storefront", "main"))
                .getId();
        environmentId = environmentService.create(applicationId, new EnvironmentRequest("prod", null)).getId();
        Deployment first = deploymentService.deploy(environmentId, new DeploymentRequest("aaaaaaaaaaaa", "1.0.0", null));
        deploymentService.updateStatus(first.getId(), DeploymentStatus.SUCCESS, null);
        Deployment second = deploymentService.deploy(environmentId, new DeploymentRequest("bbbbbbbbbbbb", "2.0.0", null));
        deploymentId = deploymentService.updateStatus(second.getId(), DeploymentStatus.SUCCESS, null).getId();
    }

    @Test
    void rendersDashboard() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("storefront")))
                .andExpect(content().string(containsString("100%")));
    }

    @Test
    void rendersApplicationPages() throws Exception {
        mvc.perform(get("/applications")).andExpect(status().isOk())
                .andExpect(content().string(containsString("storefront")));
        mvc.perform(get("/applications/new")).andExpect(status().isOk());
        mvc.perform(get("/applications/" + applicationId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Customer web shop")))
                .andExpect(content().string(containsString("https://github.com/acme/storefront")))
                .andExpect(content().string(containsString(" UTC")));
    }

    @Test
    void rendersEnvironmentWithRollbackOption() throws Exception {
        mvc.perform(get("/environments/" + environmentId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rollbackTarget"))
                .andExpect(content().string(containsString("Roll back to")));
    }

    @Test
    void showsOperationalStateAndLastDeploymentOnDashboard() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Last deployment")))
                .andExpect(content().string(containsString(">UNVERIFIED<")))
                .andExpect(content().string(containsString("#" + deploymentId)));
    }

    @Test
    void highlightsFailedDeploymentOnEnvironmentAndDashboard() throws Exception {
        Deployment failed = deploymentService.deploy(environmentId, new DeploymentRequest("cccccccccccc", "3.0.0", null));
        deploymentService.updateStatus(failed.getId(), DeploymentStatus.FAILED, "ECS service did not stabilize");

        mvc.perform(get("/environments/" + environmentId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">DEGRADED<")))
                .andExpect(content().string(containsString("failed</strong>")))
                .andExpect(content().string(containsString("ECS service did not stabilize")))
                .andExpect(content().string(containsString("is still serving traffic")));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("1</span> need attention")));
        mvc.perform(get("/applications/" + applicationId))
                .andExpect(content().string(containsString(">DEGRADED<")));
        mvc.perform(get("/deployments/" + failed.getId()))
                .andExpect(content().string(containsString("This deployment failed")))
                .andExpect(content().string(containsString("ECS service did not stabilize")));
    }

    @Test
    void showsInProgressDeploymentOnEnvironmentPage() throws Exception {
        Deployment running = deploymentService.deploy(environmentId, new DeploymentRequest("cccccccccccc", "3.0.0", null));

        mvc.perform(get("/environments/" + environmentId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">DEPLOYING<")))
                .andExpect(content().string(containsString("blocked until it finishes")));
        mvc.perform(get("/deployments/" + running.getId()))
                .andExpect(content().string(containsString("Waiting for the pipeline to start")));
    }

    @Test
    void marksOnlyTheLiveDeploymentAsLive() throws Exception {
        Long previousId = deploymentService.findByEnvironment(environmentId).getLast().getId();

        mvc.perform(get("/deployments/" + deploymentId))
                .andExpect(model().attribute("live", true))
                .andExpect(content().string(containsString("currently serving traffic")));
        mvc.perform(get("/deployments/" + previousId))
                .andExpect(model().attribute("live", false));
    }

    @Test
    void rendersDeploymentHistoryAndDetail() throws Exception {
        mvc.perform(get("/deployments").param("status", "SUCCESS")).andExpect(status().isOk())
                .andExpect(content().string(containsString("2.0.0")));
        mvc.perform(get("/deployments/" + deploymentId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("bbbbbbbbbbbb")));
    }

    @Test
    void registersApplicationThroughForm() throws Exception {
        mvc.perform(post("/applications")
                        .param("name", "checkout")
                        .param("repositoryUrl", "https://github.com/acme/checkout")
                        .param("defaultBranch", "main"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/applications/*"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void redisplaysFormWithValidationErrors() throws Exception {
        mvc.perform(post("/applications").param("name", "").param("repositoryUrl", "not-a-url"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("applicationForm", "name", "repositoryUrl"));
    }

    @Test
    void triggersRollbackFromEnvironmentPage() throws Exception {
        mvc.perform(post("/environments/" + environmentId + "/rollback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/deployments/*"))
                .andExpect(flash().attribute("success", containsString("Rollback to 1.0.0")));
    }

    @Test
    void deploysThroughForm() throws Exception {
        mvc.perform(post("/environments/" + environmentId + "/deployments")
                        .param("imageTag", "cccccccccccc").param("version", "3.0.0").param("message", "release"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/deployments/*"))
                .andExpect(flash().attribute("success", containsString("of 3.0.0 created (PENDING)")));
    }

    @Test
    void rejectsInvalidDeploymentsFromForm() throws Exception {
        String environmentUrl = "/environments/" + environmentId;
        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", ""))
                .andExpect(redirectedUrl(environmentUrl))
                .andExpect(flash().attribute("error", containsString("Deployment not created: imageTag")));
        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", "latest"))
                .andExpect(redirectedUrl(environmentUrl))
                .andExpect(flash().attribute("error", containsString("'latest' is not allowed")));
        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", "cccccccccccc").param("version", "3.0\r\n"))
                .andExpect(redirectedUrl(environmentUrl))
                .andExpect(flash().attribute("error", containsString("version must not contain line breaks")));
        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", "bbbbbbbbbbbb"))
                .andExpect(redirectedUrl(environmentUrl))
                .andExpect(flash().attribute("error", containsString("already live")));

        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", "cccccccccccc"));
        mvc.perform(post(environmentUrl + "/deployments").param("imageTag", "dddddddddddd"))
                .andExpect(redirectedUrl(environmentUrl))
                .andExpect(flash().attribute("error", containsString("already has a deployment in progress")));
    }

    @Test
    void reportsStatusManuallyAndRejectsInvalidReports() throws Exception {
        Deployment pending = deploymentService.deploy(environmentId, new DeploymentRequest("cccccccccccc", "3.0.0", null));
        String statusUrl = "/deployments/" + pending.getId() + "/status";

        mvc.perform(post(statusUrl).param("status", "SUCCESS"))
                .andExpect(redirectedUrl("/deployments/" + pending.getId()))
                .andExpect(flash().attribute("success", "Deployment marked SUCCESS"));
        mvc.perform(post(statusUrl).param("status", "FAILED"))
                .andExpect(flash().attribute("error", containsString("cannot move from SUCCESS to FAILED")));
        mvc.perform(post(statusUrl).param("status", "ROLLED_BACK"))
                .andExpect(flash().attribute("error", containsString("cannot be reported")));
        mvc.perform(get("/deployments/" + pending.getId()))
                .andExpect(model().attribute("nextStatuses", List.of()));
    }

    @Test
    void rejectsRollbackToAnotherEnvironmentsDeployment() throws Exception {
        Long stagingId = environmentService.create(applicationId, new EnvironmentRequest("staging", null)).getId();
        Deployment staging = deploymentService.deploy(stagingId, new DeploymentRequest("cccccccccccc", null, null));

        mvc.perform(post("/environments/" + environmentId + "/rollback")
                        .param("targetDeploymentId", staging.getId().toString()))
                .andExpect(redirectedUrl("/environments/" + environmentId))
                .andExpect(flash().attribute("error", containsString("belongs to environment 'staging'")));
    }

    @Test
    void showsNotFoundPageForUnknownEnvironment() throws Exception {
        mvc.perform(get("/environments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("was not found")));
    }
}
