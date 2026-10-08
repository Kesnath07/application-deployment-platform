package com.controlcenter.service;

import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.repository.DeploymentRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rolls an environment back to an earlier successful image. A rollback is a regular
 * deployment of an already-built immutable image tag, so the pipeline skips the build
 * and only updates the ECS service. Once the rollback deployment succeeds, the deployment
 * it replaced is marked ROLLED_BACK (see {@link DeploymentService#updateStatus}).
 */
@Service
public class RollbackService {

    private static final Logger log = LoggerFactory.getLogger(RollbackService.class);

    private final DeploymentRepository deployments;
    private final DeploymentService deploymentService;
    private final TransactionTemplate transactions;

    public RollbackService(DeploymentRepository deployments, DeploymentService deploymentService,
                           PlatformTransactionManager transactionManager) {
        this.deployments = deployments;
        this.deploymentService = deploymentService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** The successful deployment currently live in the environment, if any. */
    public Optional<Deployment> currentDeployment(Long environmentId) {
        return successfulDeployments(environmentId).stream().findFirst();
    }

    /** Successful deployments whose image differs from the live one, newest first. */
    public List<Deployment> rollbackCandidates(Long environmentId) {
        List<Deployment> successful = successfulDeployments(environmentId);
        if (successful.isEmpty()) {
            return List.of();
        }
        String liveImage = successful.getFirst().getImageTag();
        return successful.stream()
                .skip(1)
                .filter(deployment -> !deployment.getImageTag().equals(liveImage))
                .toList();
    }

    public Deployment rollback(Long environmentId, Long targetDeploymentId, String reason) {
        Long rollbackId = transactions.execute(status -> {
            Deployment current = currentDeployment(environmentId).orElseThrow(() ->
                    new ConflictException("Environment has no successful deployment to roll back from"));
            Deployment target = resolveTarget(environmentId, targetDeploymentId);

            String note = "Rollback from %s to %s (deployment #%d)"
                    .formatted(current.getVersion(), target.getVersion(), target.getId());
            if (reason != null && !reason.isBlank()) {
                note += ": " + reason.trim();
            }
            Deployment rollback = deploymentService.startDeployment(environmentId, target.getVersion(),
                    target.getImageTag(), true, note);
            log.info("Rolling back environment={} from deployment={} to deployment={} via deployment={}",
                    environmentId, current.getId(), target.getId(), rollback.getId());
            return rollback.getId();
        });
        return deploymentService.dispatch(Objects.requireNonNull(rollbackId));
    }

    private Deployment resolveTarget(Long environmentId, Long targetDeploymentId) {
        List<Deployment> candidates = rollbackCandidates(environmentId);
        if (targetDeploymentId == null) {
            return candidates.stream().findFirst().orElseThrow(() ->
                    new ConflictException("No previous successful version is available to roll back to"));
        }
        Deployment requested = deployments.findById(targetDeploymentId)
                .orElseThrow(() -> new NotFoundException("Deployment", targetDeploymentId));
        if (!requested.getEnvironment().getId().equals(environmentId)) {
            throw new IllegalArgumentException(("Deployment #%d belongs to environment '%s' of application '%s', "
                    + "not to this environment").formatted(targetDeploymentId, requested.getEnvironment().getName(),
                            requested.getApplication().getName()));
        }
        return candidates.stream()
                .filter(candidate -> candidate.getId().equals(requested.getId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(("Deployment #%d is not a valid rollback target: it must be a "
                        + "successful deployment of this environment with a different image than the live one")
                        .formatted(targetDeploymentId)));
    }

    private List<Deployment> successfulDeployments(Long environmentId) {
        return deployments.findByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(environmentId,
                DeploymentStatus.SUCCESS);
    }
}
