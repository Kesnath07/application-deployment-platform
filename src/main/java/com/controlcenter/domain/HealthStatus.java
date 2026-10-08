package com.controlcenter.domain;

/** Result of the most recent HTTP health probe against an environment's endpoint. */
public enum HealthStatus {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY
}
