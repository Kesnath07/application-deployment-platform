package com.controlcenter.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A deployment target for an application, e.g. {@code dev} or {@code prod}.
 * In AWS every environment maps to its own ECS service behind its own ALB.
 */
@Entity
@Table(name = "environments")
public class Environment {

    public static final int HEALTH_DETAIL_LIMIT = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Column(nullable = false, updatable = false, length = 30)
    private String name;

    /** Base URL of the running workload, used for health probes (optional). */
    @Column(length = 500)
    private String url;

    @Column(name = "current_version", length = 100)
    private String currentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvironmentStatus status = EnvironmentStatus.NOT_DEPLOYED;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    /** Why the most recent probe failed, e.g. "HTTP 503" or "Connection timed out"; null otherwise. */
    @Column(name = "last_health_detail", length = HEALTH_DETAIL_LIMIT)
    private String healthDetail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Environment() {
    }

    public Environment(Application application, String name, String url) {
        this.application = application;
        this.name = name;
        this.url = url;
    }

    public void updateUrl(String url) {
        this.url = url;
        this.healthStatus = HealthStatus.UNKNOWN;
        this.lastHealthCheckAt = null;
        this.healthDetail = null;
    }

    public void deploymentStarted() {
        this.status = EnvironmentStatus.DEPLOYING;
    }

    public void deploymentSucceeded(String version) {
        this.currentVersion = version;
        this.status = EnvironmentStatus.ACTIVE;
    }

    /**
     * A failed deployment leaves the previous version running (ECS keeps the old tasks
     * until the new ones are healthy), so an environment with a live version stays ACTIVE.
     */
    public void deploymentFailed() {
        this.status = currentVersion == null ? EnvironmentStatus.FAILED : EnvironmentStatus.ACTIVE;
    }

    public void recordHealth(HealthStatus healthStatus, Instant checkedAt) {
        recordHealth(healthStatus, null, checkedAt);
    }

    /** Stores a probe result; the detail is kept only for failed probes and capped to the column size. */
    public void recordHealth(HealthStatus healthStatus, String detail, Instant checkedAt) {
        this.healthStatus = healthStatus;
        this.lastHealthCheckAt = checkedAt;
        this.healthDetail = healthStatus != HealthStatus.UNHEALTHY || detail == null || detail.isBlank() ? null
                : detail.length() <= HEALTH_DETAIL_LIMIT ? detail : detail.substring(0, HEALTH_DETAIL_LIMIT - 1) + "…";
    }

    public Long getId() {
        return id;
    }

    public Application getApplication() {
        return application;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public EnvironmentStatus getStatus() {
        return status;
    }

    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    public Instant getLastHealthCheckAt() {
        return lastHealthCheckAt;
    }

    public String getHealthDetail() {
        return healthDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
