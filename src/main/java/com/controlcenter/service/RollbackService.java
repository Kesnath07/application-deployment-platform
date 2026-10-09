package com.controlcenter.service;

import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.repository.DeploymentRepository;
import com.controlcenter.repository.EnvironmentRepository;
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

    static final int REASON_LIMIT = 500;

    private final DeploymentRepository deployments;
    private final EnvironmentRepository environments;
    private final DeploymentService deploymentService;
    private final TransactionTemplate transactions;

    public RollbackService(DeploymentRepository deployments, EnvironmentRepository environments,
                           DeploymentService deploymentService, PlatformTransactionManager transactionManager) {
        this.deployments = deployments;
        this.environments = environments;
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

    /**
     * Starts a rollback deployment of {@code targetDeploymentId}, or of the previous successful
     * version when it is null. The target deployment itself is never modified: the rollback is a
     * new history entry, and only a successful rollback marks the replaced deployment ROLLED_BACK.
     */
    public Deployment rollback(Long environmentId, Long targetDeploymentId, String reason) {
        String trimmedReason = requireValidReason(reason);
        Long rollbackId = transactions.execute(status -> {
            Environment environment = environments.findWithApplicationById(environmentId)
                    .orElseThrow(() -> new NotFoundException("Environment", environmentId));
            Deployment current = currentDeployment(environmentId).orElseThrow(() -> new ConflictException(
                    "Environment '%s' has no successful deployment to roll back from".formatted(environment.getName())));
            Deployment target = resolveTarget(environment, current, targetDeploymentId);

            String note = "Rollback from %s to %s (deployment #%d)"
                    .formatted(current.getVersion(), target.getVersion(), target.getId());
            if (trimmedReason != null) {
                note += ": " + trimmedReason;
            }
            Deployment rollback = deploymentService.startDeployment(environmentId, target.getVersion(),
                    target.getImageTag(), true, note);
            log.info("Rolling back environment={} from deployment={} to deployment={} via deployment={}",
                    environmentId, current.getId(), target.getId(), rollback.getId());
            return rollback.getId();
        });
        return deploymentService.dispatch(Objects.requireNonNull(rollbackId));
    }

    private Deployment resolveTarget(Environment environment, Deployment current, Long targetDeploymentId) {
        List<Deployment> candidates = rollbackCandidates(environment.getId());
        if (targetDeploymentId == null) {
            return candidates.stream().findFirst().orElseThrow(() -> new ConflictException(("No previous successful "
                    + "version is available to roll back to: every successful deployment of '%s' runs the live "
                    + "image '%s'").formatted(environment.getName(), current.getImageTag())));
        }
        Deployment requested = deployments.findWithDetailsById(targetDeploymentId)
                .orElseThrow(() -> new NotFoundException("Deployment", targetDeploymentId));
        if (!requested.getEnvironment().getId().equals(environment.getId())) {
            throw new IllegalArgumentException(("Deployment #%d belongs to environment '%s' of application '%s', "
                    + "not to this environment").formatted(targetDeploymentId, requested.getEnvironment().getName(),
                            requested.getApplication().getName()));
        }
        return candidates.stream()
                .filter(candidate -> candidate.getId().equals(requested.getId()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Deployment #%d is not a valid rollback target: %s"
                        .formatted(targetDeploymentId, ineligibility(requested, current))));
    }

    /** Explains why a deployment of this environment cannot be restored. */
    private static String ineligibility(Deployment requested, Deployment current) {
        if (requested.getId().equals(current.getId())) {
            return "it is the deployment that is currently live";
        }
        return switch (requested.getStatus()) {
            case PENDING, RUNNING -> "it is still in progress";
            case FAILED -> "it failed and never went live";
            case ROLLED_BACK -> "it was rolled back; deploy image '%s' again to restore it"
                    .formatted(requested.getImageTag());
            case SUCCESS -> "it runs image '%s', which is already live".formatted(requested.getImageTag());
        };
    }

    private static String requireValidReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > REASON_LIMIT) {
            throw new IllegalArgumentException("Rollback reason must be at most %d characters".formatted(REASON_LIMIT));
        }
        return trimmed;
    }

    private List<Deployment> successfulDeployments(Long environmentId) {
        return deployments.findByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(environmentId,
                DeploymentStatus.SUCCESS);
    }
}
