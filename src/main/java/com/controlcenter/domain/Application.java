package com.controlcenter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A deployable application (one Git repository producing one container image). */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Application() {
    }

    public Application(String name, String description, String repositoryUrl, String defaultBranch) {
        this.name = name;
        update(description, repositoryUrl, defaultBranch);
    }

    public void update(String description, String repositoryUrl, String defaultBranch) {
        this.description = description;
        this.repositoryUrl = repositoryUrl;
        this.defaultBranch = (defaultBranch == null || defaultBranch.isBlank()) ? "main" : defaultBranch;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
