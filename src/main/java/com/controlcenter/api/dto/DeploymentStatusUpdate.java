package com.controlcenter.api.dto;

import com.controlcenter.domain.DeploymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Status report sent by the CI/CD pipeline (or an operator) while a deployment progresses. */
public record DeploymentStatusUpdate(
        @NotNull
        DeploymentStatus status,

        @Size(max = 1000)
        String message) {
}
