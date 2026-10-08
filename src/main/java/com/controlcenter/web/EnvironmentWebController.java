package com.controlcenter.web;

import com.controlcenter.api.dto.ValidationPatterns;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.service.EnvironmentHealthChecker;
import com.controlcenter.service.EnvironmentService;
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

    public EnvironmentWebController(EnvironmentService environmentService, EnvironmentHealthChecker healthChecker) {
        this.environmentService = environmentService;
        this.healthChecker = healthChecker;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("environment", environmentService.get(id));
        return "environments/detail";
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
