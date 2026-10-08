package com.controlcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level settings. Every value can be overridden through environment
 * variables (e.g. {@code CONTROL_CENTER_VERSION}), which is how ECS injects configuration.
 *
 * @param version     version identifier of the running build (the image tag in AWS)
 * @param environment name of the environment this instance runs in (local, dev, prod)
 */
@ConfigurationProperties(prefix = "control-center")
public record ControlCenterProperties(String version, String environment) {

    public ControlCenterProperties {
        version = (version == null || version.isBlank()) ? "local" : version;
        environment = (environment == null || environment.isBlank()) ? "local" : environment;
    }
}
