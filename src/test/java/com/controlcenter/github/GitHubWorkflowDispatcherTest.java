package com.controlcenter.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.controlcenter.config.GitHubProperties;
import com.controlcenter.support.StubHttpServer;
import com.controlcenter.support.StubHttpServer.RecordedRequest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitHubWorkflowDispatcherTest {

    private static final WorkflowDispatchRequest REQUEST = new WorkflowDispatchRequest(
            "https://github.com/acme/payments-api", "main", "prod", "3f2a9c1e5b7d", 42L);

    private StubHttpServer github;

    @BeforeEach
    void startServer() throws Exception {
        github = new StubHttpServer().respondWith(204, "");
    }

    @AfterEach
    void stopServer() {
        github.close();
    }

    @Test
    void dispatchesWorkflowWithInputs() {
        DispatchResult result = dispatcher(true, "test-token").dispatch(REQUEST);

        assertThat(result.outcome()).isEqualTo(DispatchResult.Outcome.DISPATCHED);
        RecordedRequest request = github.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/repos/acme/payments-api/actions/workflows/deploy.yml/dispatches");
        assertThat(request.authorization()).isEqualTo("Bearer test-token");
        assertThat(request.body())
                .contains("\"ref\":\"main\"")
                .contains("\"environment\":\"prod\"")
                .contains("\"image_tag\":\"3f2a9c1e5b7d\"")
                .contains("\"deployment_id\":\"42\"");
    }

    @Test
    void skipsWhenIntegrationIsDisabled() {
        DispatchResult result = dispatcher(false, "test-token").dispatch(REQUEST);

        assertThat(result.outcome()).isEqualTo(DispatchResult.Outcome.SKIPPED);
        assertThat(result.message()).contains("/api/deployments/42/status");
        assertThat(github.requests()).isEmpty();
    }

    @Test
    void skipsWhenTokenIsMissing() {
        assertThat(dispatcher(true, "").dispatch(REQUEST).outcome()).isEqualTo(DispatchResult.Outcome.SKIPPED);
    }

    @Test
    void reportsFailureWhenGitHubRejectsTheRequest() {
        github.respondWith(404, "{\"message\":\"Not Found\"}");

        DispatchResult result = dispatcher(true, "test-token").dispatch(REQUEST);

        assertThat(result.outcome()).isEqualTo(DispatchResult.Outcome.FAILED);
        assertThat(result.message()).contains("404");
    }

    @Test
    void reportsFailureWhenGitHubIsUnreachable() {
        GitHubProperties properties = new GitHubProperties(true, "token", "http://127.0.0.1:1", null,
                Duration.ofSeconds(1));

        DispatchResult result = new GitHubWorkflowDispatcher(properties, RestClient.builder()).dispatch(REQUEST);

        assertThat(result.outcome()).isEqualTo(DispatchResult.Outcome.FAILED);
    }

    private GitHubWorkflowDispatcher dispatcher(boolean enabled, String token) {
        GitHubProperties properties = new GitHubProperties(enabled, token, github.baseUrl(), "deploy.yml",
                Duration.ofSeconds(2));
        return new GitHubWorkflowDispatcher(properties, RestClient.builder());
    }
}
