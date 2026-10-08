package com.controlcenter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnvironmentRequest(
        @NotBlank
        @Pattern(regexp = ValidationPatterns.ENVIRONMENT_NAME, message = ValidationPatterns.ENVIRONMENT_NAME_MESSAGE)
        String name,

        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.HTTP_URL_MESSAGE)
        String url) {
}
