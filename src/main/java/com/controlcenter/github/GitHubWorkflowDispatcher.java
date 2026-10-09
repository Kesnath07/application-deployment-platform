package com.controlcenter.github;

import com.controlcenter.config.GitHubProperties;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Triggers the repository's deployment workflow through the GitHub REST API
 * ({@code POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches}).
 */
@Component
public class GitHubWorkflowDispatcher implements WorkflowDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubWorkflowDispatcher.class);
    private static final Pattern REPOSITORY = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    private final GitHubProperties properties;
    private final RestClient restClient;

    public GitHubWorkflowDispatcher(GitHubProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        if (properties.enabled() && !properties.isConfigured()) {
            log.warn("GITHUB_DISPATCH_ENABLED is true but GITHUB_TOKEN is empty: deployments will be recorded "
                    + "but no workflow will be dispatched");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public DispatchResult dispatch(WorkflowDispatchRequest request) {
        if (!properties.isConfigured()) {
            String reason = properties.enabled() ? "GitHub dispatch is enabled but no GITHUB_TOKEN is configured"
                    : "GitHub dispatch is disabled";
            return DispatchResult.skipped(reason + ": run the '%s' workflow manually and report "
                    .formatted(properties.workflowFile()) + "the result via POST /api/deployments/"
                    + request.deploymentId() + "/status");
        }
        Matcher matcher = REPOSITORY.matcher(request.repositoryUrl());
        if (!matcher.matches()) {
            return DispatchResult.failed("Unsupported repository URL: " + request.repositoryUrl());
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        Map<String, Object> body = Map.of(
                "ref", request.ref(),
                "inputs", Map.of(
                        "environment", request.environment(),
                        "image_tag", request.imageTag(),
                        "deployment_id", String.valueOf(request.deploymentId())));
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches",
                            owner, repo, properties.workflowFile())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Dispatched workflow {} on {}/{}@{} for deployment {}",
                    properties.workflowFile(), owner, repo, request.ref(), request.deploymentId());
            return DispatchResult.dispatched("Workflow '%s' dispatched on %s/%s (%s)"
                    .formatted(properties.workflowFile(), owner, repo, request.ref()));
        } catch (RestClientResponseException ex) {
            log.warn("GitHub rejected workflow dispatch for deployment {}: {} {}",
                    request.deploymentId(), ex.getStatusCode(), ex.getResponseBodyAsString());
            return DispatchResult.failed("GitHub API returned " + ex.getStatusCode().value()
                    + " while dispatching the deployment workflow");
        } catch (RestClientException ex) {
            log.warn("GitHub unreachable for deployment {}: {}", request.deploymentId(), ex.getMessage());
            return DispatchResult.failed("Could not reach the GitHub API: " + ex.getMessage());
        }
    }
}
