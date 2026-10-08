package com.controlcenter.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnvironmentUpdateRequest(
        @Size(max = 500)
        @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.HTTP_URL_MESSAGE)
        String url) {
}
