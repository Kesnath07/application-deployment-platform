package com.controlcenter.domain;

/** Deployment state of an environment, derived from its most recent deployment. */
public enum EnvironmentStatus {
    NOT_DEPLOYED,
    DEPLOYING,
    ACTIVE,
    FAILED
}
