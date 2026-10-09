package com.controlcenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to roll out an image to an environment.
 *
 * @param imageTag immutable image tag, normally the full Git commit SHA. {@code latest} is rejected
 * @param version  human-readable, single-line version label; defaults to the shortened image tag
 * @param message  optional change description shown in the deployment history
 */
public record DeploymentRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.IMAGE_TAG, message = ValidationPatterns.IMAGE_TAG_MESSAGE)
        String imageTag,

        @Size(max = ValidationPatterns.VERSION_MAX_LENGTH)
        @Pattern(regexp = ValidationPatterns.VERSION, message = ValidationPatterns.VERSION_MESSAGE)
        String version,

        @Size(max = ValidationPatterns.MESSAGE_MAX_LENGTH)
        String message) {
}
