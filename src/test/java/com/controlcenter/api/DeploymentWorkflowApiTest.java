package com.controlcenter.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.github.DispatchResult;
import com.controlcenter.github.WorkflowDispatcher;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Drives the deployment lifecycle through the REST API the way the dashboard and the GitHub
 * Actions workflow do, against the real services and the Flyway-managed schema.
 */
@IntegrationTest
@AutoConfigureMockMvc
class DeploymentWorkflowApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @MockitoBean
    private WorkflowDispatcher dispatcher;

    private long applicationId;
    private long prodId;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.dispatched("Workflow dispatched"));
        applicationId = id(postJson("/api/applications",
                "{\"name\": \"ledger\", \"repositoryUrl\": \"https://github.com/acme/ledger\"}"));
        prodId = createEnvironment("prod");
    }

    @Test
    void runsAReleaseFailureAndRollbackCycle() throws Exception {
        long v1 = deploy("aaaaaaaaaaaa", "1.0.0").andExpect(jsonPath("$.status").value("RUNNING")).id();
        report(v1, "SUCCESS", "service stable").andExpect(status().isOk());
        environmentStatus()
                .andExpect(jsonPath("$.state").value("UNVERIFIED"))
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.liveDeployment.id").value(v1));

        long v2 = deploy("bbbbbbbbbbbb", "2.0.0").id();
        report(v2, "SUCCESS", "service stable");
        long v3 = deploy("cccccccccccc", "3.0.0").id();
        report(v3, "FAILED", "ECS deployment circuit breaker triggered")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.completedAt").exists());

        environmentStatus()
                .andExpect(jsonPath("$.state").value("DEGRADED"))
                .andExpect(jsonPath("$.deploymentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentVersion").value("2.0.0"))
                .andExpect(jsonPath("$.liveDeployment.id").value(v2))
                .andExpect(jsonPath("$.latestDeployment.id").value(v3))
                .andExpect(jsonPath("$.failureReason").value("ECS deployment circuit breaker triggered"));

        long rollback = id(postJson("/api/environments/" + prodId + "/rollback", "{\"reason\": \"bad release\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rollback").value(true))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.imageTag").value("aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.message").value(containsString("Rollback from 2.0.0 to 1.0.0"))));
        environmentStatus().andExpect(jsonPath("$.state").value("DEPLOYING"));
        // The replaced deployment stays SUCCESS until the rollback itself has succeeded.
        mvc.perform(get("/api/deployments/" + v2)).andExpect(jsonPath("$.status").value("SUCCESS"));

        report(rollback, "SUCCESS", "service stable");

        mvc.perform(get("/api/deployments/" + v2)).andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        mvc.perform(get("/api/deployments").param("status", "ROLLED_BACK"))
                .andExpect(jsonPath("$.totalElements").value(1));
        environmentStatus()
                .andExpect(jsonPath("$.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.liveDeployment.id").value(rollback))
                .andExpect(jsonPath("$.failureReason").value(nullValue()));
        mvc.perform(get("/api/environments/" + prodId + "/deployments"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(rollback));
    }

    @Test
    void failedFirstDeploymentLeavesNothingLiveAndCanBeRetried() throws Exception {
        long first = deploy("aaaaaaaaaaaa", "1.0.0").id();
        report(first, "FAILED", "image not found in ECR");

        environmentStatus()
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.deploymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.currentVersion").value(nullValue()))
                .andExpect(jsonPath("$.liveDeployment").value(nullValue()));

        long retry = deploy("aaaaaaaaaaaa", "1.0.0").andExpect(jsonPath("$.status").value("RUNNING")).id();
        report(retry, "SUCCESS", "service stable");

        environmentStatus().andExpect(jsonPath("$.currentVersion").value("1.0.0"));
    }

    @Test
    void pipelineCanCompleteADeploymentThatWasNeverDispatched() throws Exception {
        when(dispatcher.dispatch(any())).thenReturn(DispatchResult.skipped("GitHub integration disabled"));

        long deployment = deploy("aaaaaaaaaaaa", "1.0.0")
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.startedAt").value(nullValue()))
                .id();
        report(deployment, "SUCCESS", "reported manually")
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.completedAt").exists());

        environmentStatus().andExpect(jsonPath("$.deploymentStatus").value("ACTIVE"));
    }

    @Test
    void reportsEveryEnvironmentOfTheApplication() throws Exception {
        long devId = createEnvironment("dev");
        report(deploy("aaaaaaaaaaaa", "1.0.0").id(), "SUCCESS", null);

        mvc.perform(get("/api/applications/" + applicationId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].environmentId").value(devId))
                .andExpect(jsonPath("$[0].state").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$[1].environmentId").value(prodId))
                .andExpect(jsonPath("$[1].state").value("UNVERIFIED"));
        mvc.perform(get("/api/applications/999999/status")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidDeploymentRequests() throws Exception {
        postJson("/api/environments/999999/deployments", "{\"imageTag\": \"aaaaaaaaaaaa\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Environment with id 999999 was not found"));
        postJson("/api/environments/" + prodId + "/deployments", "{\"imageTag\": \"latest\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("'latest' is not allowed")));
        postJson("/api/environments/" + prodId + "/deployments", "{\"imageTag\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        postJson("/api/environments/" + prodId + "/deployments",
                "{\"imageTag\": \"aaaaaaaaaaaa\", \"version\": \"" + "v".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("version")));
        postJson("/api/environments/" + prodId + "/deployments", "{\"imageTag\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        long running = deploy("aaaaaaaaaaaa", "1.0.0").id();
        postJson("/api/environments/" + prodId + "/deployments", "{\"imageTag\": \"bbbbbbbbbbbb\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Environment 'prod' already has a deployment in progress"));

        report(running, "SUCCESS", null);
        postJson("/api/environments/" + prodId + "/deployments", "{\"imageTag\": \"aaaaaaaaaaaa\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already live in environment 'prod'")));

        mvc.perform(get("/api/environments/" + prodId + "/deployments")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidStatusReports() throws Exception {
        long deployment = deploy("aaaaaaaaaaaa", "1.0.0").id();
        report(deployment, "RUNNING", "retry of the started callback")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("cannot move from RUNNING to RUNNING")));
        report(deployment, "SUCCESS", "service stable").andExpect(status().isOk());
        // curl --retry in the deploy workflow may resend a callback that was already applied.
        report(deployment, "SUCCESS", "service stable")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        report(deployment, "ROLLED_BACK", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ROLLED_BACK is set automatically")));
        report(deployment, "PENDING", null).andExpect(status().isBadRequest());
        report(deployment, "FAILED", "late failure callback").andExpect(status().isConflict());
        report(999_999L, "SUCCESS", null).andExpect(status().isNotFound());
        postJson("/api/deployments/" + deployment + "/status", "{\"status\": \"DONE\"}")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/deployments/" + deployment))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Workflow dispatched\nservice stable"));
    }

    @Test
    void rejectsInvalidRollbacks() throws Exception {
        postJson("/api/environments/999999/rollback", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Environment with id 999999 was not found"));
        postJson("/api/environments/" + prodId + "/rollback", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Environment 'prod' has no successful deployment to roll back from"));

        report(deploy("aaaaaaaaaaaa", "1.0.0").id(), "SUCCESS", null);
        postJson("/api/environments/" + prodId + "/rollback", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No previous successful version is available to roll back to: "
                        + "every successful deployment of 'prod' runs the live image 'aaaaaaaaaaaa'"));
        rollbackTo(0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("targetDeploymentId")));

        long live = deploy("bbbbbbbbbbbb", "2.0.0").id();
        report(live, "SUCCESS", null);
        long stagingId = createEnvironment("staging");
        long staging = id(postJson("/api/environments/" + stagingId + "/deployments",
                "{\"imageTag\": \"cccccccccccc\"}"));
        report(staging, "SUCCESS", null);

        rollbackTo(staging)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("belongs to environment 'staging'")));
        rollbackTo(999_999L).andExpect(status().isNotFound());
        rollbackTo(live)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("not a valid rollback target")));

        deploy("dddddddddddd", "3.0.0");
        postJson("/api/environments/" + prodId + "/rollback", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already has a deployment in progress")));
    }

    private long createEnvironment(String name) throws Exception {
        return id(postJson("/api/applications/" + applicationId + "/environments", "{\"name\": \"" + name + "\"}"));
    }

    private CreatedDeployment deploy(String imageTag, String version) throws Exception {
        ResultActions result = postJson("/api/environments/" + prodId + "/deployments",
                "{\"imageTag\": \"" + imageTag + "\", \"version\": \"" + version + "\"}")
                .andExpect(status().isCreated());
        return new CreatedDeployment(result);
    }

    private ResultActions report(long deploymentId, String status, String message) throws Exception {
        String body = message == null
                ? "{\"status\": \"" + status + "\"}"
                : "{\"status\": \"" + status + "\", \"message\": \"" + message + "\"}";
        return postJson("/api/deployments/" + deploymentId + "/status", body);
    }

    private ResultActions rollbackTo(long targetDeploymentId) throws Exception {
        return postJson("/api/environments/" + prodId + "/rollback",
                "{\"targetDeploymentId\": " + targetDeploymentId + "}");
    }

    private ResultActions environmentStatus() throws Exception {
        return mvc.perform(get("/api/environments/" + prodId + "/status")).andExpect(status().isOk());
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** Lets a test assert on the creation response and then keep the new deployment's id. */
    private record CreatedDeployment(ResultActions result) {

        CreatedDeployment andExpect(ResultMatcher matcher) throws Exception {
            result.andExpect(matcher);
            return this;
        }

        long id() throws Exception {
            return DeploymentWorkflowApiTest.id(result);
        }
    }
}
