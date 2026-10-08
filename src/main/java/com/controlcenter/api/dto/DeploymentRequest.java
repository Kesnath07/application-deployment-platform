package com.controlcenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to roll out an image to an environment.
 *
 * @param imageTag immutable image tag, normally the full Git commit SHA. {@code latest} is rejected
 * @param version  human-readable version label; defaults to the shortened image tag
 * @param message  optional change description shown in the deployment history
 */
public record DeploymentRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.IMAGE_TAG, message = ValidationPatterns.IMAGE_TAG_MESSAGE)
        String imageTag,

        @Size(max = 100)
        String version,

        @Size(max = 1000)
        String message) {
}
