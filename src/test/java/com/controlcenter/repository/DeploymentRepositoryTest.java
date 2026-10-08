package com.controlcenter.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.controlcenter.domain.Application;
import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import com.controlcenter.domain.Environment;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the Flyway-managed schema to verify mappings, constraints and derived queries. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeploymentRepositoryTest {

    @Autowired
    private DeploymentRepository deployments;
    @Autowired
    private EnvironmentRepository environments;
    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private TestEntityManager entityManager;

    private Environment dev;

    @BeforeEach
    void setUp() {
        Application application = applications.save(
                new Application("inventory", null, "https://github.com/acme/inventory", "main"));
        dev = environments.save(new Environment(application, "dev", null));
    }

    @Test
    void returnsSuccessfulDeploymentsNewestFirst() {
        Deployment first = save("aaa", DeploymentStatus.SUCCESS, Instant.parse("2026-01-01T10:00:00Z"));
        save("bbb", DeploymentStatus.FAILED, Instant.parse("2026-01-02T10:00:00Z"));
        Deployment third = save("ccc", DeploymentStatus.SUCCESS, Instant.parse("2026-01-03T10:00:00Z"));

        List<Deployment> successful = deployments.findByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(
                dev.getId(), DeploymentStatus.SUCCESS);

        assertThat(successful).extracting(Deployment::getId).containsExactly(third.getId(), first.getId());
    }

    @Test
    void detectsInProgressDeployments() {
        save("aaa", DeploymentStatus.SUCCESS, Instant.now());
        assertThat(deployments.existsByEnvironmentIdAndStatusIn(dev.getId(), DeploymentStatus.IN_PROGRESS)).isFalse();

        deployments.save(new Deployment(dev, "2.0", "bbb", false, null));
        assertThat(deployments.existsByEnvironmentIdAndStatusIn(dev.getId(), DeploymentStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void filtersAndPagesHistoryByStatus() {
        save("aaa", DeploymentStatus.SUCCESS, Instant.now());
        save("bbb", DeploymentStatus.FAILED, Instant.now());
        save("ccc", DeploymentStatus.FAILED, Instant.now());

        assertThat(deployments.findByStatusOrderByCreatedAtDescIdDesc(DeploymentStatus.FAILED, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
        assertThat(deployments.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 2)).getTotalPages()).isEqualTo(2);
        assertThat(deployments.countByStatus(DeploymentStatus.SUCCESS)).isEqualTo(1);
    }

    @Test
    void enforcesUniqueEnvironmentNamePerApplication() {
        Environment duplicate = new Environment(dev.getApplication(), "dev", null);

        assertThatThrownBy(() -> environments.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Deployment save(String imageTag, DeploymentStatus status, Instant completedAt) {
        Deployment deployment = new Deployment(dev, imageTag, imageTag, false, null);
        if (status != DeploymentStatus.PENDING) {
            deployment.transitionTo(status, null, completedAt);
        }
        return entityManager.persistAndFlush(deployment);
    }
}
