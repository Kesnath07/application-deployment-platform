package com.controlcenter.repository;

import com.controlcenter.domain.Deployment;
import com.controlcenter.domain.DeploymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    @EntityGraph(attributePaths = {"application", "environment"})
    Optional<Deployment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"application", "environment"})
    Page<Deployment> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"application", "environment"})
    Page<Deployment> findByStatusOrderByCreatedAtDescIdDesc(DeploymentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"application", "environment"})
    List<Deployment> findByEnvironmentIdOrderByCreatedAtDescIdDesc(Long environmentId);

    @EntityGraph(attributePaths = {"application", "environment"})
    List<Deployment> findByApplicationIdOrderByCreatedAtDescIdDesc(Long applicationId, Pageable pageable);

    /** Successful deployments of an environment, newest first: the rollback candidates. */
    List<Deployment> findByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(Long environmentId,
                                                                           DeploymentStatus status);

    Optional<Deployment> findFirstByEnvironmentIdAndStatusOrderByCompletedAtDescIdDesc(Long environmentId,
                                                                                    DeploymentStatus status);

    boolean existsByEnvironmentIdAndStatusIn(Long environmentId, Collection<DeploymentStatus> statuses);

    long countByStatus(DeploymentStatus status);
}
