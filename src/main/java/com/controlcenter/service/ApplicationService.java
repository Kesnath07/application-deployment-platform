package com.controlcenter.service;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.ApplicationUpdateRequest;
import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Application;
import com.controlcenter.repository.ApplicationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applications;

    public ApplicationService(ApplicationRepository applications) {
        this.applications = applications;
    }

    public List<Application> findAll() {
        return applications.findAllByOrderByNameAsc();
    }

    public Application get(Long id) {
        return applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
    }

    @Transactional
    public Application register(ApplicationRequest request) {
        String name = request.name().trim();
        if (applications.existsByNameIgnoreCase(name)) {
            throw new ConflictException("An application named '%s' already exists".formatted(name));
        }
        Application application = applications.save(new Application(name, blankToNull(request.description()),
                normalizeRepositoryUrl(request.repositoryUrl()), request.defaultBranch()));
        log.info("Registered application id={} name={}", application.getId(), application.getName());
        return application;
    }

    @Transactional
    public Application update(Long id, ApplicationUpdateRequest request) {
        Application application = get(id);
        application.update(blankToNull(request.description()), normalizeRepositoryUrl(request.repositoryUrl()),
                request.defaultBranch());
        return application;
    }

    static String normalizeRepositoryUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
