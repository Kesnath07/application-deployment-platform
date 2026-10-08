package com.controlcenter.api.dto;

import com.controlcenter.domain.Application;
import java.time.Instant;

public record ApplicationResponse(Long id, String name, String description, String repositoryUrl,
                                  String defaultBranch, Instant createdAt, Instant updatedAt) {

    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(application.getId(), application.getName(), application.getDescription(),
                application.getRepositoryUrl(), application.getDefaultBranch(),
                application.getCreatedAt(), application.getUpdatedAt());
    }
}
