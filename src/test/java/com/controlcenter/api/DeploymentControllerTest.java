package com.controlcenter.api;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.common.ConflictException;
import com.controlcenter.domain.Application;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.service.DeploymentService;
import com.controlcenter.service.RollbackService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeploymentController.class)
class DeploymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DeploymentService deploymentService;
    @MockitoBean
    private RollbackService rollbackService;

    @Test
    void createsDeployment() throws Exception {
        when(deploymentService.deploy(eq(3L), any())).thenReturn(deployment(10L, "abc123def456", false));

        mvc.perform(post("/api/environments/3/deployments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageTag\": \"abc123def456\", \"message\": \"release\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.environmentName").value("dev"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsInvalidImageTag() throws Exception {
        mvc.perform(post("/api/environments/3/deployments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageTag\": \"-bad tag\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("imageTag")));
    }

    @Test
    void rejectsMultiLineVersionLabel() throws Exception {
        mvc.perform(post("/api/environments/3/deployments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageTag\": \"abc123def456\", \"version\": \"1.0.0\\nSUCCESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]")
                        .value("version: must not contain line breaks or other control characters"));
    }

    @Test
    void acceptsPipelineStatusCallback() throws Exception {
        Deployment deployment = deployment(10L, "abc123def456", false);
        deployment.transitionTo(DeploymentStatus.SUCCESS, "service stable", Instant.now());
        when(deploymentService.updateStatus(10L, DeploymentStatus.SUCCESS, "service stable")).thenReturn(deployment);

        mvc.perform(post("/api/deployments/10/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUCCESS\", \"message\": \"service stable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void returnsConflictForInvalidTransition() throws Exception {
        when(deploymentService.updateStatus(10L, DeploymentStatus.RUNNING, null))
                .thenThrow(new ConflictException("Deployment 10 cannot move from FAILED to RUNNING"));

        mvc.perform(post("/api/deployments/10/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RUNNING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenImageIsAlreadyLive() throws Exception {
        when(deploymentService.deploy(eq(3L), any()))
                .thenThrow(new ConflictException("Image 'abc123def456' is already live in environment 'dev'"));

        mvc.perform(post("/api/environments/3/deployments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageTag\": \"abc123def456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Image 'abc123def456' is already live")));
    }

    @Test
    void returnsBadRequestForStatusThatCannotBeReported() throws Exception {
        when(deploymentService.updateStatus(10L, DeploymentStatus.ROLLED_BACK, null))
                .thenThrow(new IllegalArgumentException("Status ROLLED_BACK cannot be reported"));

        mvc.perform(post("/api/deployments/10/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ROLLED_BACK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Status ROLLED_BACK cannot be reported"));
    }

    @Test
    void rejectsStatusCallbackWithoutStatus() throws Exception {
        mvc.perform(post("/api/deployments/10/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"done\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(startsWith("status")));
    }

    @Test
    void rollsBackWithoutRequestBody() throws Exception {
        when(rollbackService.rollback(eq(3L), isNull(), isNull())).thenReturn(deployment(11L, "aaaaaaaaaaaa", true));

        mvc.perform(post("/api/environments/3/rollback"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollback").value(true))
                .andExpect(jsonPath("$.imageTag").value("aaaaaaaaaaaa"));
    }

    @Test
    void rollsBackToExplicitTarget() throws Exception {
        when(rollbackService.rollback(3L, 4L, "bad release")).thenReturn(deployment(12L, "bbbbbbbbbbbb", true));

        mvc.perform(post("/api/environments/3/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDeploymentId\": 4, \"reason\": \"bad release\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12));
    }

    private static Deployment deployment(Long id, String imageTag, boolean rollback) {
        Application application = new Application("billing", null, "https://github.com/acme/billing", "main");
        ReflectionTestUtils.setField(application, "id", 1L);
        Environment environment = new Environment(application, "dev", null);
        ReflectionTestUtils.setField(environment, "id", 3L);
        Deployment deployment = new Deployment(environment, imageTag, imageTag, rollback, null);
        ReflectionTestUtils.setField(deployment, "id", id);
        return deployment;
    }
}
