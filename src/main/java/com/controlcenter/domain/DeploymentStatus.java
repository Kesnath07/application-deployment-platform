package com.controlcenter.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a deployment. Transitions are deliberately strict so that pipeline
 * callbacks arriving out of order cannot corrupt the deployment history.
 */
public enum DeploymentStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    ROLLED_BACK;

    public static final Set<DeploymentStatus> IN_PROGRESS = EnumSet.of(PENDING, RUNNING);

    public boolean canTransitionTo(DeploymentStatus target) {
        return switch (this) {
            case PENDING -> target == RUNNING || target == SUCCESS || target == FAILED;
            case RUNNING -> target == SUCCESS || target == FAILED;
            case SUCCESS -> target == ROLLED_BACK;
            case FAILED, ROLLED_BACK -> false;
        };
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == ROLLED_BACK;
    }
}
