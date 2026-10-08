package com.controlcenter.repository;

import com.controlcenter.domain.Environment;
import com.controlcenter.domain.EnvironmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    @EntityGraph(attributePaths = "application")
    Optional<Environment> findWithApplicationById(Long id);

    List<Environment> findByApplicationIdOrderByNameAsc(Long applicationId);

    @EntityGraph(attributePaths = "application")
    List<Environment> findWithApplicationByApplicationIdOrderByNameAsc(Long applicationId);

    @EntityGraph(attributePaths = "application")
    List<Environment> findAllByOrderByApplicationNameAscNameAsc();

    List<Environment> findByUrlIsNotNull();

    boolean existsByApplicationIdAndNameIgnoreCase(Long applicationId, String name);

    long countByStatus(EnvironmentStatus status);
}
