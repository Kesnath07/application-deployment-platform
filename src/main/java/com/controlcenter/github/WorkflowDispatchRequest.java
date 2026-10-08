package com.controlcenter.github;

/**
 * Everything the deployment workflow needs to roll out one image to one environment.
 *
 * @param repositoryUrl GitHub repository hosting the workflow
 * @param ref           branch the workflow definition is taken from
 * @param environment   target environment (maps to a GitHub environment + ECS service)
 * @param imageTag      immutable image tag (Git commit SHA) to deploy
 * @param deploymentId  control-center deployment id, echoed back in status callbacks
 */
public record WorkflowDispatchRequest(String repositoryUrl, String ref, String environment,
                                      String imageTag, Long deploymentId) {
}
