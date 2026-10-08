package com.controlcenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The application name is its stable identifier and cannot be changed after registration. */
public record ApplicationUpdateRequest(
        @Size(max = 1000)
        String description,

        @NotBlank
        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.GITHUB_REPOSITORY_URL, message = ValidationPatterns.GITHUB_REPOSITORY_URL_MESSAGE)
        String repositoryUrl,

        @Size(max = 100)
        @Pattern(regexp = ValidationPatterns.BRANCH, message = ValidationPatterns.BRANCH_MESSAGE)
        String defaultBranch) {
}
