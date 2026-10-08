package com.controlcenter.repository;

import com.controlcenter.domain.Application;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
