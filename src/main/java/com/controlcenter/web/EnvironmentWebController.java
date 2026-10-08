package com.controlcenter.web;

import com.controlcenter.api.dto.ValidationPatterns;
import com.controlcenter.common.ConflictException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.service.DeploymentService;
import com.controlcenter.service.EnvironmentHealthChecker;
import com.controlcenter.service.EnvironmentOverview;
import com.controlcenter.service.EnvironmentOverviewService;
import com.controlcenter.service.EnvironmentService;
import com.controlcenter.service.RollbackService;
import com.controlcenter.web.form.DeploymentForm;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/environments")
public class EnvironmentWebController {

    private final EnvironmentService environmentService;
    private final EnvironmentHealthChecker healthChecker;
    private final DeploymentService deploymentService;
    private final RollbackService rollbackService;
    private final EnvironmentOverviewService overviewService;

    public EnvironmentWebController(EnvironmentService environmentService, EnvironmentHealthChecker healthChecker,
                                    DeploymentService deploymentService, RollbackService rollbackService,
                                    EnvironmentOverviewService overviewService) {
        this.environmentService = environmentService;
        this.healthChecker = healthChecker;
        this.deploymentService = deploymentService;
        this.rollbackService = rollbackService;
        this.overviewService = overviewService;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        EnvironmentOverview overview = overviewService.forEnvironment(id);
        model.addAttribute("overview", overview);
        model.addAttribute("environment", overview.environment());
        model.addAttribute("deployments", deploymentService.findByEnvironment(id));
        model.addAttribute("deploymentForm", new DeploymentForm());
        List<Deployment> candidates = rollbackService.rollbackCandidates(id);
        model.addAttribute("currentDeployment", rollbackService.currentDeployment(id).orElse(null));
        model.addAttribute("rollbackTarget", candidates.isEmpty() ? null : candidates.getFirst());
        model.addAttribute("rollbackCandidateIds", candidates.stream().map(Deployment::getId).collect(Collectors.toSet()));
        return "environments/detail";
    }

    @PostMapping("/{id}/rollback")
    public String rollback(@PathVariable Long id, @RequestParam(required = false) Long targetDeploymentId,
                           RedirectAttributes redirect) {
        try {
            Deployment rollback = rollbackService.rollback(id, targetDeploymentId, null);
            redirect.addFlashAttribute("success", "Rollback to %s started as deployment #%d"
                    .formatted(rollback.getVersion(), rollback.getId()));
            return "redirect:/deployments/" + rollback.getId();
        } catch (ConflictException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/environments/" + id;
        }
    }

    @PostMapping("/{id}/url")
    public String updateUrl(@PathVariable Long id, @RequestParam(required = false) String url,
                            RedirectAttributes redirect) {
        if (url != null && url.length() <= 500 && url.matches(ValidationPatterns.HTTP_URL)) {
            environmentService.updateUrl(id, url);
            redirect.addFlashAttribute("success", "Environment URL updated");
        } else {
            redirect.addFlashAttribute("error", "URL " + ValidationPatterns.HTTP_URL_MESSAGE);
        }
        return "redirect:/environments/" + id;
    }

    @PostMapping("/{id}/health-check")
    public String checkHealth(@PathVariable Long id, RedirectAttributes redirect) {
        Environment environment = environmentService.get(id);
        HealthStatus status = healthChecker.check(environment);
        if (status == HealthStatus.UNKNOWN) {
            redirect.addFlashAttribute("error", "Configure an environment URL to enable health checks");
        } else {
            redirect.addFlashAttribute("success", "Health check completed: " + status);
        }
        return "redirect:/environments/" + id;
    }
}
