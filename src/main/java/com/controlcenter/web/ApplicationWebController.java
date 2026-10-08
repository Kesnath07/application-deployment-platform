package com.controlcenter.web;

import com.controlcenter.common.ConflictException;
import com.controlcenter.domain.Application;
import com.controlcenter.domain.Environment;
import com.controlcenter.service.ApplicationService;
import com.controlcenter.service.EnvironmentService;
import com.controlcenter.web.form.ApplicationForm;
import com.controlcenter.web.form.EnvironmentForm;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/applications")
public class ApplicationWebController {

    private final ApplicationService applicationService;
    private final EnvironmentService environmentService;

    public ApplicationWebController(ApplicationService applicationService, EnvironmentService environmentService) {
        this.applicationService = applicationService;
        this.environmentService = environmentService;
    }

    @GetMapping
    public String list(Model model) {
        Map<Long, List<Environment>> environmentsByApplication = environmentService.findAll().stream()
                .collect(Collectors.groupingBy(environment -> environment.getApplication().getId()));
        model.addAttribute("applications", applicationService.findAll());
        model.addAttribute("environmentsByApplication", environmentsByApplication);
        return "applications/list";
    }

    @GetMapping("/new")
    public String newApplication(Model model) {
        model.addAttribute("applicationForm", new ApplicationForm());
        return "applications/form";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("applicationForm") ApplicationForm form, BindingResult result,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "applications/form";
        }
        try {
            Application application = applicationService.register(form.toRequest());
            redirect.addFlashAttribute("success", "Application '%s' registered".formatted(application.getName()));
            return "redirect:/applications/" + application.getId();
        } catch (ConflictException ex) {
            result.rejectValue("name", "duplicate", ex.getMessage());
            return "applications/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("application", applicationService.get(id));
        model.addAttribute("environments", environmentService.findByApplication(id));
        model.addAttribute("environmentForm", new EnvironmentForm());
        return "applications/detail";
    }

    @PostMapping("/{id}/environments")
    public String createEnvironment(@PathVariable Long id, @Valid @ModelAttribute EnvironmentForm form,
                                    BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", "Environment not created: " + FormErrors.summarize(result));
            return "redirect:/applications/" + id;
        }
        try {
            Environment environment = environmentService.create(id, form.toRequest());
            redirect.addFlashAttribute("success", "Environment '%s' created".formatted(environment.getName()));
        } catch (ConflictException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/applications/" + id;
    }
}
