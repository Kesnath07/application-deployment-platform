package com.controlcenter.web;

import com.controlcenter.common.ConflictException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.service.DeploymentService;
import com.controlcenter.web.form.DeploymentForm;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DeploymentWebController {

    private static final int PAGE_SIZE = 20;

    private final DeploymentService deploymentService;

    public DeploymentWebController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping("/environments/{environmentId}/deployments")
    public String deploy(@PathVariable Long environmentId, @Valid @ModelAttribute DeploymentForm form,
                         BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", "Deployment not created: " + FormErrors.summarize(result));
            return "redirect:/environments/" + environmentId;
        }
        try {
            Deployment deployment = deploymentService.deploy(environmentId, form.toRequest());
            redirect.addFlashAttribute("success", "Deployment #%d of %s created (%s)"
                    .formatted(deployment.getId(), deployment.getVersion(), deployment.getStatus()));
            return "redirect:/deployments/" + deployment.getId();
        } catch (ConflictException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/environments/" + environmentId;
        }
    }

    @GetMapping("/deployments")
    public String history(@RequestParam(required = false) DeploymentStatus status,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        Page<Deployment> deployments = deploymentService.findAll(status, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        model.addAttribute("deployments", deployments);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", DeploymentStatus.values());
        return "deployments/list";
    }

    @GetMapping("/deployments/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Deployment deployment = deploymentService.get(id);
        model.addAttribute("deployment", deployment);
        model.addAttribute("live", deploymentService.isLive(deployment));
        model.addAttribute("nextStatuses", Arrays.stream(DeploymentStatus.values())
                .filter(status -> status != DeploymentStatus.ROLLED_BACK && deployment.getStatus().canTransitionTo(status))
                .toList());
        return "deployments/detail";
    }

    /** Manual status reporting for pipelines that run without the automatic callback. */
    @PostMapping("/deployments/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam DeploymentStatus status,
                               RedirectAttributes redirect) {
        try {
            deploymentService.updateStatus(id, status, "Marked %s manually from the dashboard".formatted(status));
            redirect.addFlashAttribute("success", "Deployment marked " + status);
        } catch (ConflictException | IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/deployments/" + id;
    }
}
