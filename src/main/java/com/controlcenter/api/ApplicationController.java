package com.controlcenter.api;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.ApplicationResponse;
import com.controlcenter.api.dto.ApplicationUpdateRequest;
import com.controlcenter.service.ApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return applicationService.findAll().stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return ApplicationResponse.from(applicationService.get(id));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> register(@Valid @RequestBody ApplicationRequest request) {
        ApplicationResponse created = ApplicationResponse.from(applicationService.register(request));
        return ResponseEntity.created(URI.create("/api/applications/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody ApplicationUpdateRequest request) {
        return ApplicationResponse.from(applicationService.update(id, request));
    }
}
