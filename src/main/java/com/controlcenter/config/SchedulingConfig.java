package com.controlcenter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs are only enabled when environment health checks are switched on. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "control-center.health-check", name = "enabled", havingValue = "true")
public class SchedulingConfig {
}
