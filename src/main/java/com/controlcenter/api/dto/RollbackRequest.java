package com.controlcenter.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param targetDeploymentId successful deployment to restore; when omitted the previous
 *                           successful version of the environment is used
 * @param reason             optional explanation recorded in the deployment history
 */
public record RollbackRequest(@Positive Long targetDeploymentId, @Size(max = 500) String reason) {
}
