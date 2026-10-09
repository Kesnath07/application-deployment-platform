package com.controlcenter.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Writes rows with plain SQL, bypassing the entities, to prove that the Flyway schema itself
 * rejects data the application would never produce.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long billing;
    private long billingDev;
    private long payments;

    @BeforeEach
    void setUp() {
        billing = application("billing");
        payments = application("payments");
        billingDev = environment(billing, "dev");
    }

    @Test
    void acceptsConsistentRows() {
        assertThatCode(() -> {
            deployment(billing, billingDev, "PENDING", "NULL", "NULL", "abc123");
            deployment(billing, billingDev, "RUNNING", "CURRENT_TIMESTAMP", "NULL", "abc124");
            deployment(billing, billingDev, "SUCCESS", "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", "abc125");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsADeploymentWhoseApplicationDoesNotOwnTheEnvironment() {
        assertThatThrownBy(() -> deployment(payments, billingDev, "PENDING", "NULL", "NULL", "abc123"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTimestampsThatContradictTheStatus() {
        assertThatThrownBy(() -> deployment(billing, billingDev, "PENDING", "CURRENT_TIMESTAMP", "NULL", "a1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> deployment(billing, billingDev, "RUNNING", "NULL", "NULL", "a2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> deployment(billing, billingDev, "SUCCESS", "CURRENT_TIMESTAMP", "NULL", "a3"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> deployment(billing, billingDev, "FAILED",
                "TIMESTAMP WITH TIME ZONE '2026-01-01 10:05:00+00'",
                "TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00'", "a4"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMutableOrBlankImageTagsAndBlankVersions() {
        assertThatThrownBy(() -> deployment(billing, billingDev, "PENDING", "NULL", "NULL", "latest"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> deployment(billing, billingDev, "PENDING", "NULL", "NULL", "LATEST"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> deployment(billing, billingDev, "PENDING", "NULL", "NULL", " "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO deployments (application_id, environment_id, version, image_tag, status, created_at)
                VALUES (?, ?, '  ', 'abc123', 'PENDING', CURRENT_TIMESTAMP)""", billing, billingDev))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInconsistentEnvironmentState() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE environments SET status = 'ACTIVE', current_version = NULL WHERE id = ?", billingDev))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE environments SET health_status = 'HEALTHY', last_health_check_at = NULL WHERE id = ?",
                billingDev))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE environments SET health_status = 'HEALTHY', last_health_check_at = CURRENT_TIMESTAMP,
                    last_health_detail = 'HTTP 503' WHERE id = ?""", billingDev))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("""
                UPDATE environments SET health_status = 'UNHEALTHY', last_health_check_at = CURRENT_TIMESTAMP,
                    last_health_detail = 'HTTP 503' WHERE id = ?""", billingDev)).isEqualTo(1);
    }

    @Test
    void deletingAnApplicationStillCascadesToItsHistory() {
        deployment(billing, billingDev, "SUCCESS", "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", "abc123");

        jdbc.update("DELETE FROM applications WHERE id = ?", billing);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployments", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM environments", Long.class)).isZero();
    }

    private long application(String name) {
        jdbc.update("""
                INSERT INTO applications (name, repository_url, default_branch, created_at, updated_at)
                VALUES (?, ?, 'main', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
                name, "https://github.com/acme/" + name);
        return jdbc.queryForObject("SELECT id FROM applications WHERE name = ?", Long.class, name);
    }

    private long environment(long applicationId, String name) {
        jdbc.update("""
                INSERT INTO environments (application_id, name, status, health_status, created_at, updated_at)
                VALUES (?, ?, 'NOT_DEPLOYED', 'UNKNOWN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
                applicationId, name);
        return jdbc.queryForObject("SELECT id FROM environments WHERE application_id = ? AND name = ?",
                Long.class, applicationId, name);
    }

    /** Timestamps are SQL expressions so tests can pass NULL or literals. */
    private void deployment(long applicationId, long environmentId, String status, String startedAt,
                            String completedAt, String imageTag) {
        jdbc.update("""
                INSERT INTO deployments (application_id, environment_id, version, image_tag, status,
                                         created_at, started_at, completed_at)
                VALUES (?, ?, '1.0.0', ?, ?, CURRENT_TIMESTAMP, %s, %s)""".formatted(startedAt, completedAt),
                applicationId, environmentId, imageTag, status);
    }
}
