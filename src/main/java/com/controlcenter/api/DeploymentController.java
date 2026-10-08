package com.controlcenter.api;

import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.DeploymentResponse;
import com.controlcenter.api.dto.DeploymentStatusUpdate;
import com.controlcenter.api.dto.PageResponse;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.service.DeploymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    /** Creates a deployment and triggers the GitHub Actions deployment workflow. */
    @PostMapping("/api/environments/{environmentId}/deployments")
    public ResponseEntity<DeploymentResponse> deploy(@PathVariable Long environmentId,
                                                     @Valid @RequestBody DeploymentRequest request) {
        DeploymentResponse created = DeploymentResponse.from(deploymentService.deploy(environmentId, request));
        return ResponseEntity.created(URI.create("/api/deployments/" + created.id())).body(created);
    }

    @GetMapping("/api/environments/{environmentId}/deployments")
    public List<DeploymentResponse> listForEnvironment(@PathVariable Long environmentId) {
        return deploymentService.findByEnvironment(environmentId).stream().map(DeploymentResponse::from).toList();
    }

    @GetMapping("/api/deployments")
    public PageResponse<DeploymentResponse> list(@RequestParam(required = false) DeploymentStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(deploymentService.findAll(status, pageable).map(DeploymentResponse::from));
    }

    @GetMapping("/api/deployments/{id}")
    public DeploymentResponse get(@PathVariable Long id) {
        return DeploymentResponse.from(deploymentService.get(id));
    }

    /**
     * Status callback used by the deployment workflow: RUNNING when the rollout starts,
     * SUCCESS once ECS reports the service stable and the health check passes, FAILED otherwise.
     */
    @PostMapping("/api/deployments/{id}/status")
    public DeploymentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody DeploymentStatusUpdate update) {
        return DeploymentResponse.from(deploymentService.updateStatus(id, update.status(), update.message()));
    }
}
