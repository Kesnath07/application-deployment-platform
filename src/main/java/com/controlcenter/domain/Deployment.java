package com.controlcenter.domain;

import com.controlcenter.common.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A single rollout of an immutable container image (identified by {@code imageTag},
 * normally the Git commit SHA) to one environment.
 */
@Entity
@Table(name = "deployments")
public class Deployment {

    private static final int MESSAGE_LIMIT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(name = "image_tag", nullable = false, length = 128)
    private String imageTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status = DeploymentStatus.PENDING;

    @Column(name = "is_rollback", nullable = false)
    private boolean rollback;

    @Column(length = 1000)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Deployment() {
    }

    public Deployment(Environment environment, String version, String imageTag, boolean rollback, String message) {
        this.application = environment.getApplication();
        this.environment = environment;
        this.version = version;
        this.imageTag = imageTag;
        this.rollback = rollback;
        appendMessage(message);
    }

    /**
     * Moves the deployment to {@code target}, enforcing the allowed lifecycle and
     * maintaining the start/completion timestamps.
     */
    public void transitionTo(DeploymentStatus target, String message, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Deployment %d cannot move from %s to %s".formatted(id, status, target));
        }
        if (target == DeploymentStatus.RUNNING || startedAt == null) {
            startedAt = now;
        }
        if (target == DeploymentStatus.SUCCESS || target == DeploymentStatus.FAILED) {
            completedAt = now;
        }
        this.status = target;
        appendMessage(message);
    }

    /**
     * Appends a note to the deployment's message log, keeping the most recent entries
     * when the column limit is reached.
     */
    public void appendMessage(String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        String combined = message == null ? note.trim() : message + "\n" + note.trim();
        this.message = combined.length() <= MESSAGE_LIMIT ? combined : combined.substring(combined.length() - MESSAGE_LIMIT);
    }

    public boolean isInProgress() {
        return DeploymentStatus.IN_PROGRESS.contains(status);
    }

    /** The most recent entry of the message log, e.g. the pipeline's explanation of a failure. */
    public String getLatestNote() {
        return message == null ? null : message.substring(message.lastIndexOf('\n') + 1);
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getVersion() {
        return version;
    }

    public String getImageTag() {
        return imageTag;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public boolean isRollback() {
        return rollback;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
