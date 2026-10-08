package com.controlcenter.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
    void showsNotFoundPageForUnknownEnvironment() throws Exception {
        mvc.perform(get("/environments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("was not found")));
    }
}
