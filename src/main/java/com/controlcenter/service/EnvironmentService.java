package com.controlcenter.service;

import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Application;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.repository.ApplicationRepository;
import com.controlcenter.repository.EnvironmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvironmentRepository environments;
    private final ApplicationRepository applications;
    private final Clock clock;

    public EnvironmentService(EnvironmentRepository environments, ApplicationRepository applications, Clock clock) {
        this.environments = environments;
        this.applications = applications;
        this.clock = clock;
    }

    public Environment get(Long id) {
        return environments.findWithApplicationById(id).orElseThrow(() -> new NotFoundException("Environment", id));
    }

    public List<Environment> findByApplication(Long applicationId) {
        if (!applications.existsById(applicationId)) {
            throw new NotFoundException("Application", applicationId);
        }
        return environments.findByApplicationIdOrderByNameAsc(applicationId);
    }

    public List<Environment> findAll() {
        return environments.findAllByOrderByApplicationNameAscNameAsc();
    }

    @Transactional
    public Environment create(Long applicationId, EnvironmentRequest request) {
        Application application = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application", applicationId));
        String name = request.name().trim().toLowerCase();
        if (environments.existsByApplicationIdAndNameIgnoreCase(applicationId, name)) {
            throw new ConflictException("Application '%s' already has an environment named '%s'"
                    .formatted(application.getName(), name));
        }
        Environment environment = environments.save(new Environment(application, name, normalizeUrl(request.url())));
        log.info("Created environment id={} name={} application={}", environment.getId(), name, application.getName());
        return environment;
    }

    @Transactional
    public Environment updateUrl(Long id, String url) {
        Environment environment = get(id);
        environment.updateUrl(normalizeUrl(url));
        return environment;
    }

    @Transactional
    public void recordHealth(Long id, HealthStatus status, String detail) {
        environments.findById(id)
                .ifPresent(environment -> environment.recordHealth(status, detail, Instant.now(clock)));
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
