package com.controlcenter.repository;

import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    @EntityGraph(attributePaths = "application")
    Optional<Environment> findWithApplicationById(Long id);

    /**
     * Loads the environment with a row lock held until the transaction ends. Starting a deployment
     * takes this lock first, so concurrent requests for the same environment are serialized and the
     * "one deployment in progress" check cannot be raced.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Environment> findForUpdateById(Long id);

    List<Environment> findByApplicationIdOrderByNameAsc(Long applicationId);

    @EntityGraph(attributePaths = "application")
    List<Environment> findWithApplicationByApplicationIdOrderByNameAsc(Long applicationId);

    @EntityGraph(attributePaths = "application")
    List<Environment> findAllByOrderByApplicationNameAscNameAsc();

    List<Environment> findByUrlIsNotNull();

    boolean existsByApplicationIdAndNameIgnoreCase(Long applicationId, String name);

    long countByStatus(EnvironmentStatus status);
}
