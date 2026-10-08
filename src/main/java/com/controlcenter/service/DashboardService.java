package com.controlcenter.service;

import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.repository.ApplicationRepository;
import com.controlcenter.repository.DeploymentRepository;
import com.controlcenter.repository.EnvironmentRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_DEPLOYMENTS = 10;

    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;

    public DashboardService(ApplicationRepository applications, EnvironmentRepository environments,
                            DeploymentRepository deployments) {
        this.applications = applications;
        this.environments = environments;
        this.deployments = deployments;
    }

    public DashboardSummary summary() {
        List<Environment> allEnvironments = environments.findAllByOrderByApplicationNameAscNameAsc();
        long successful = deployments.countByStatus(DeploymentStatus.SUCCESS)
                + deployments.countByStatus(DeploymentStatus.ROLLED_BACK);
        long failed = deployments.countByStatus(DeploymentStatus.FAILED);
        long inProgress = deployments.countByStatus(DeploymentStatus.PENDING)
                + deployments.countByStatus(DeploymentStatus.RUNNING);
        long finished = successful + failed;
        Integer successRate = finished == 0 ? null : (int) Math.round(successful * 100.0 / finished);
        long unhealthy = allEnvironments.stream()
                .filter(environment -> environment.getHealthStatus() == HealthStatus.UNHEALTHY).count();
        List<Deployment> recent = deployments
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, RECENT_DEPLOYMENTS)).getContent();

        return new DashboardSummary(applications.count(), allEnvironments.size(),
                environments.countByStatus(EnvironmentStatus.ACTIVE), unhealthy, deployments.count(),
                inProgress, failed, successRate, allEnvironments, recent);
    }

    /**
     * @param successRate percentage of finished deployments that succeeded (rolled-back
     *                    deployments count as successful rollouts); null when none finished yet
     */
    public record DashboardSummary(long applicationCount, long environmentCount, long activeEnvironmentCount,
                                   long unhealthyEnvironmentCount, long deploymentCount, long inProgressCount,
                                   long failedCount, Integer successRate, List<Environment> environments,
                                   List<Deployment> recentDeployments) {
    }
}
