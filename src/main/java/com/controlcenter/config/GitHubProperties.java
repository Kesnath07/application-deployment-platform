package com.controlcenter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub Actions integration used to trigger the deployment workflow.
 *
 * @param enabled      when false, deployments are recorded but no workflow is dispatched
 * @param token        fine-grained token with "Actions: write" on the target repositories;
 *                     injected from AWS Secrets Manager, never configured in files
 * @param apiUrl       GitHub REST API base URL
 * @param workflowFile workflow file name (in .github/workflows) that performs the deployment
 * @param timeout      HTTP timeout for GitHub API calls
 */
@ConfigurationProperties(prefix = "control-center.github")
public record GitHubProperties(boolean enabled, String token, String apiUrl, String workflowFile, Duration timeout) {

    public GitHubProperties {
        apiUrl = (apiUrl == null || apiUrl.isBlank()) ? "https://api.github.com" : apiUrl;
        workflowFile = (workflowFile == null || workflowFile.isBlank()) ? "deploy.yml" : workflowFile;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    public boolean isConfigured() {
        return enabled && token != null && !token.isBlank();
    }
}
