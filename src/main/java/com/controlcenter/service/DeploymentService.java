package com.controlcenter.service;

import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.ValidationPatterns;
import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import com.controlcenter.github.DispatchResult;
import com.controlcenter.github.WorkflowDispatchRequest;
import com.controlcenter.github.WorkflowDispatcher;
import com.controlcenter.repository.DeploymentRepository;
import com.controlcenter.repository.EnvironmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the deployment lifecycle. A deployment is recorded first, then the CI/CD
 * workflow is dispatched outside of the database transaction, and finally the
 * pipeline reports progress back through {@link #updateStatus}.
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    /** Statuses a pipeline or operator may report. ROLLED_BACK is only set when a rollback succeeds. */
    static final Set<DeploymentStatus> REPORTABLE_STATUSES =
            EnumSet.of(DeploymentStatus.RUNNING, DeploymentStatus.SUCCESS, DeploymentStatus.FAILED);

    private final DeploymentRepository deployments;
    private final EnvironmentRepository environments;
    private final WorkflowDispatcher dispatcher;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public DeploymentService(DeploymentRepository deployments, EnvironmentRepository environments,
                             WorkflowDispatcher dispatcher, PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.deployments = deployments;
        this.environments = environments;
        this.dispatcher = dispatcher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    public Deployment get(Long id) {
        return deployments.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Deployment", id));
    }

    public Page<Deployment> findAll(DeploymentStatus status, Pageable pageable) {
        return status == null
                ? deployments.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : deployments.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
    }

    public List<Deployment> findRecentByApplication(Long applicationId, int limit) {
        return deployments.findByApplicationIdOrderByCreatedAtDescIdDesc(applicationId, PageRequest.of(0, limit));
    }

    public List<Deployment> findByEnvironment(Long environmentId) {
        requireEnvironment(environmentId);
        return deployments.findByEnvironmentIdOrderByCreatedAtDescIdDesc(environmentId);
    }

    /** Records a new deployment and triggers the deployment workflow. */
    public Deployment deploy(Long environmentId, DeploymentRequest request) {
        String imageTag = requireValidImageTag(request.imageTag());
        String version = (request.version() == null || request.version().isBlank())
                ? shorten(imageTag) : request.version().trim();
        String message = (request.message() == null || request.message().isBlank()) ? null : request.message().trim();
        Long deploymentId = transactions.execute(status ->
                startDeployment(environmentId, version, imageTag, false, message).getId());
        return dispatch(deploymentId);
    }

    /**
     * Creates the deployment row and marks the environment as deploying. Must run inside a
     * transaction. Only one deployment per environment may be in progress at a time, and the
     * image that is already live cannot be deployed again.
     */
    Deployment startDeployment(Long environmentId, String version, String imageTag, boolean rollback, String message) {
        Environment environment = requireEnvironment(environmentId);
        if (deployments.existsByEnvironmentIdAndStatusIn(environmentId, DeploymentStatus.IN_PROGRESS)) {
            throw new ConflictException("Environment '%s' already has a deployment in progress"
                    .formatted(environment.getName()));
        }
        deployments.findFirstByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(environmentId,
                        DeploymentStatus.SUCCESS)
                .filter(live -> live.getImageTag().equals(imageTag))
                .ifPresent(live -> {
                    throw new ConflictException("Image '%s' is already live in environment '%s' (deployment #%d)"
                            .formatted(imageTag, environment.getName(), live.getId()));
                });
        Deployment deployment = deployments.save(new Deployment(environment, version, imageTag, rollback, message));
        environment.deploymentStarted();
        log.info("Created deployment id={} application={} environment={} imageTag={} rollback={}",
                deployment.getId(), environment.getApplication().getName(), environment.getName(), imageTag, rollback);
        return deployment;
    }

    /** Dispatches the workflow for a freshly created deployment and records the outcome. */
    Deployment dispatch(Long deploymentId) {
        WorkflowDispatchRequest request = readOnly.execute(status -> {
            Deployment deployment = get(deploymentId);
            return new WorkflowDispatchRequest(deployment.getApplication().getRepositoryUrl(),
                    deployment.getApplication().getDefaultBranch(), deployment.getEnvironment().getName(),
                    deployment.getImageTag(), deployment.getId());
        });
        DispatchResult result = dispatcher.dispatch(request);
        return switch (result.outcome()) {
            case DISPATCHED -> updateStatus(deploymentId, DeploymentStatus.RUNNING, result.message());
            case FAILED -> updateStatus(deploymentId, DeploymentStatus.FAILED, result.message());
            case SKIPPED -> appendMessage(deploymentId, result.message());
        };
    }

    /**
     * Applies a status transition reported by the pipeline and keeps the environment's
     * status and current version consistent with it.
     */
    public Deployment updateStatus(Long deploymentId, DeploymentStatus target, String message) {
        if (!REPORTABLE_STATUSES.contains(target)) {
            throw new IllegalArgumentException(("Status %s cannot be reported; use RUNNING, SUCCESS or FAILED "
                    + "(ROLLED_BACK is set automatically when a rollback succeeds)").formatted(target));
        }
        transactions.executeWithoutResult(status -> {
            Deployment deployment = get(deploymentId);
            Environment environment = deployment.getEnvironment();
            deployment.transitionTo(target, message, Instant.now(clock));
            switch (target) {
                case RUNNING -> environment.deploymentStarted();
                case SUCCESS -> {
                    environment.deploymentSucceeded(deployment.getVersion());
                    if (deployment.isRollback()) {
                        markReplacedDeploymentRolledBack(deployment);
                    }
                }
                case FAILED -> environment.deploymentFailed();
                default -> { }
            }
            log.info("Deployment id={} environment={} moved to {}", deploymentId, environment.getName(), target);
        });
        return readOnly.execute(status -> get(deploymentId));
    }

    /** The deployment that was live before a successful rollback is flagged as rolled back. */
    private void markReplacedDeploymentRolledBack(Deployment rollback) {
        deployments.findByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(
                        rollback.getEnvironment().getId(), DeploymentStatus.SUCCESS).stream()
                .filter(previous -> !previous.getId().equals(rollback.getId()))
                .findFirst()
                .ifPresent(previous -> previous.transitionTo(DeploymentStatus.ROLLED_BACK,
                        "Rolled back by deployment #" + rollback.getId(), Instant.now(clock)));
    }

    private Deployment appendMessage(Long deploymentId, String note) {
        transactions.executeWithoutResult(status -> get(deploymentId).appendMessage(note));
        return readOnly.execute(status -> get(deploymentId));
    }

    private Environment requireEnvironment(Long environmentId) {
        return environments.findWithApplicationById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment", environmentId));
    }

    private static String requireValidImageTag(String imageTag) {
        if (imageTag == null || imageTag.isBlank()) {
            throw new IllegalArgumentException("An image tag is required");
        }
        String trimmed = imageTag.trim();
        if (!trimmed.matches(ValidationPatterns.IMAGE_TAG)) {
            throw new IllegalArgumentException("Image tag " + ValidationPatterns.IMAGE_TAG_MESSAGE);
        }
        if ("latest".equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Mutable tag 'latest' is not allowed; deploy an immutable image tag");
        }
        return trimmed;
    }

    private static String shorten(String imageTag) {
        return imageTag.length() > 12 ? imageTag.substring(0, 12) : imageTag;
    }
}
