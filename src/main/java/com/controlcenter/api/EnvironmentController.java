package com.controlcenter.api;

import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.api.dto.EnvironmentResponse;
import com.controlcenter.api.dto.EnvironmentStatusResponse;
import com.controlcenter.api.dto.EnvironmentUpdateRequest;
import com.controlcenter.service.EnvironmentHealthChecker;
import com.controlcenter.service.EnvironmentOverviewService;
import com.controlcenter.service.EnvironmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final EnvironmentHealthChecker healthChecker;
    private final EnvironmentOverviewService overviewService;

    public EnvironmentController(EnvironmentService environmentService, EnvironmentHealthChecker healthChecker,
                                 EnvironmentOverviewService overviewService) {
        this.environmentService = environmentService;
        this.healthChecker = healthChecker;
        this.overviewService = overviewService;
    }

    @GetMapping("/api/applications/{applicationId}/environments")
    public List<EnvironmentResponse> listForApplication(@PathVariable Long applicationId) {
        return environmentService.findByApplication(applicationId).stream().map(EnvironmentResponse::from).toList();
    }

    @PostMapping("/api/applications/{applicationId}/environments")
    public ResponseEntity<EnvironmentResponse> create(@PathVariable Long applicationId,
                                                      @Valid @RequestBody EnvironmentRequest request) {
        EnvironmentResponse created = EnvironmentResponse.from(environmentService.create(applicationId, request));
        return ResponseEntity.created(URI.create("/api/environments/" + created.id())).body(created);
    }

    /** Operational status of every environment of an application. */
    @GetMapping("/api/applications/{applicationId}/status")
    public List<EnvironmentStatusResponse> applicationStatus(@PathVariable Long applicationId) {
        return overviewService.forApplication(applicationId).stream().map(EnvironmentStatusResponse::from).toList();
    }

    @GetMapping("/api/environments")
    public List<EnvironmentResponse> listAll() {
        return environmentService.findAll().stream().map(EnvironmentResponse::from).toList();
    }

    @GetMapping("/api/environments/{id}")
    public EnvironmentResponse get(@PathVariable Long id) {
        return EnvironmentResponse.from(environmentService.get(id));
    }

    @PatchMapping("/api/environments/{id}")
    public EnvironmentResponse update(@PathVariable Long id, @Valid @RequestBody EnvironmentUpdateRequest request) {
        return EnvironmentResponse.from(environmentService.updateUrl(id, request.url()));
    }

    /** Operational state, health and latest/live deployment of an environment. */
    @GetMapping("/api/environments/{id}/status")
    public EnvironmentStatusResponse status(@PathVariable Long id) {
        return EnvironmentStatusResponse.from(overviewService.forEnvironment(id));
    }

    /** Runs an on-demand health probe and returns the refreshed environment. */
    @PostMapping("/api/environments/{id}/health-check")
    public EnvironmentResponse checkHealth(@PathVariable Long id) {
        healthChecker.check(environmentService.get(id));
        return EnvironmentResponse.from(environmentService.get(id));
    }
}
