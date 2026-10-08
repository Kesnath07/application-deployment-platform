package com.controlcenter.service;

import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.repository.ApplicationRepository;
import com.controlcenter.repository.DeploymentRepository;
import com.controlcenter.repository.EnvironmentRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the operational view of environments shown on the dashboard, the UI pages and the status API. */
@Service
@Transactional(readOnly = true)
public class EnvironmentOverviewService {

    private final EnvironmentRepository environments;
    private final ApplicationRepository applications;
    private final DeploymentRepository deployments;

    public EnvironmentOverviewService(EnvironmentRepository environments, ApplicationRepository applications,
                                      DeploymentRepository deployments) {
        this.environments = environments;
        this.applications = applications;
        this.deployments = deployments;
    }

    public EnvironmentOverview forEnvironment(Long environmentId) {
        Environment environment = environments.findWithApplicationById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment", environmentId));
        return overviews(List.of(environment)).getFirst();
    }

    public List<EnvironmentOverview> forApplication(Long applicationId) {
        if (!applications.existsById(applicationId)) {
            throw new NotFoundException("Application", applicationId);
        }
        return overviews(environments.findWithApplicationByApplicationIdOrderByNameAsc(applicationId));
    }

    public List<EnvironmentOverview> all() {
        return overviews(environments.findAllByOrderByApplicationNameAscNameAsc());
    }

    /** Loads the latest and the live deployment of all given environments with two queries. */
    private List<EnvironmentOverview> overviews(List<Environment> environmentList) {
        if (environmentList.isEmpty()) {
            return List.of();
        }
        List<Long> ids = environmentList.stream().map(Environment::getId).toList();
        Map<Long, Deployment> latest = byEnvironment(deployments.findLatestByEnvironmentIds(ids));
        Map<Long, Deployment> live = byEnvironment(
                deployments.findLatestByEnvironmentIdsAndStatus(ids, DeploymentStatus.SUCCESS));
        return environmentList.stream()
                .map(environment -> EnvironmentOverview.of(environment, latest.get(environment.getId()),
                        live.get(environment.getId())))
                .toList();
    }

    private static Map<Long, Deployment> byEnvironment(List<Deployment> deploymentList) {
        return deploymentList.stream()
                .collect(Collectors.toMap(deployment -> deployment.getEnvironment().getId(), Function.identity()));
    }
}
