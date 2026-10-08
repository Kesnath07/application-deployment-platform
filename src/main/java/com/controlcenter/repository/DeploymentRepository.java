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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Most recent deployment of each given environment. Identity ids grow with creation time,
     * and only one deployment per environment can be in progress, so the highest id is the latest.
     */
    @EntityGraph(attributePaths = {"application", "environment"})
    @Query("""
            select d from Deployment d where d.id in (
                select max(latest.id) from Deployment latest
                where latest.environment.id in :environmentIds
                group by latest.environment.id)
            """)
    List<Deployment> findLatestByEnvironmentIds(@Param("environmentIds") Collection<Long> environmentIds);

    /** Most recent deployment with the given status of each environment. For SUCCESS this is the live one. */
    @EntityGraph(attributePaths = {"application", "environment"})
    @Query("""
            select d from Deployment d where d.id in (
                select max(latest.id) from Deployment latest
                where latest.environment.id in :environmentIds and latest.status = :status
                group by latest.environment.id)
            """)
    List<Deployment> findLatestByEnvironmentIdsAndStatus(@Param("environmentIds") Collection<Long> environmentIds,
                                                         @Param("status") DeploymentStatus status);

    boolean existsByEnvironmentIdAndStatusIn(Long environmentId, Collection<DeploymentStatus> statuses);

    long countByStatus(DeploymentStatus status);
}
