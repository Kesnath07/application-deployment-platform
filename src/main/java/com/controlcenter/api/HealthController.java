package com.controlcenter.api;

import com.controlcenter.config.ControlCenterProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness endpoint used by the ALB target group, the ECS container
 * health check and the post-deployment verification step in GitHub Actions.
 * It intentionally does not touch the database so a transient RDS issue does not
 * cause ECS to recycle otherwise healthy tasks.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ControlCenterProperties properties;

    public HealthController(ControlCenterProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "cloud-deployment-control-center",
                properties.version(), properties.environment(), Instant.now());
    }

    public record HealthResponse(String status, String service, String version,
                                 String environment, Instant timestamp) {
    }
}
