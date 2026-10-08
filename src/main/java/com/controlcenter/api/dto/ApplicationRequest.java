package com.controlcenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplicationRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = ValidationPatterns.APPLICATION_NAME, message = ValidationPatterns.APPLICATION_NAME_MESSAGE)
        String name,

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
