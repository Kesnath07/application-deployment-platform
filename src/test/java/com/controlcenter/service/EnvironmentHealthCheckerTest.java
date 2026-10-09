package com.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.support.DatabaseCleaner;
import com.controlcenter.support.IntegrationTest;
import com.controlcenter.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class EnvironmentHealthCheckerTest {

    @Autowired
    private EnvironmentHealthChecker healthChecker;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    private StubHttpServer workload;
    private Long applicationId;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        workload = new StubHttpServer();
        applicationId = applicationService.register(
                new ApplicationRequest("catalog", null, "https://github.com/acme/catalog", "main")).getId();
    }

    @AfterEach
    void tearDown() {
        workload.close();
    }

    @Test
    void marksEnvironmentHealthyWhenEndpointReturns2xx() {
        Environment environment = environmentService.create(applicationId,
                new EnvironmentRequest("dev", workload.baseUrl()));
        workload.respondWith(200, "{\"status\":\"UP\"}");

        healthChecker.checkAll();

        Environment refreshed = environmentService.get(environment.getId());
        assertThat(refreshed.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(refreshed.getLastHealthCheckAt()).isNotNull();
        assertThat(workload.requests().getFirst().path()).isEqualTo("/api/health");
    }

    @Test
    void marksEnvironmentUnhealthyOnErrorStatus() {
        Environment environment = environmentService.create(applicationId,
                new EnvironmentRequest("prod", workload.baseUrl()));
        workload.respondWith(503, "");

        assertThat(healthChecker.check(environment)).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(environmentService.get(environment.getId()).getHealthDetail())
                .isEqualTo("HTTP 503 Service Unavailable");
    }

    @Test
    void marksEnvironmentUnhealthyWhenUnreachable() {
        Environment environment = environmentService.create(applicationId,
                new EnvironmentRequest("prod", "http://127.0.0.1:1"));

        assertThat(healthChecker.check(environment)).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(environmentService.get(environment.getId()).getHealthDetail()).isEqualTo("Connection refused");
    }

    @Test
    void clearsTheFailureDetailOnceTheEndpointRecovers() {
        Environment environment = environmentService.create(applicationId,
                new EnvironmentRequest("prod", workload.baseUrl()));
        workload.respondWith(500, "");
        healthChecker.checkAll();
        assertThat(environmentService.get(environment.getId()).getHealthDetail())
                .isEqualTo("HTTP 500 Internal Server Error");

        workload.respondWith(204, "");
        healthChecker.checkAll();

        Environment recovered = environmentService.get(environment.getId());
        assertThat(recovered.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(recovered.getHealthDetail()).isNull();
    }

    @Test
    void forgetsTheFailureDetailWhenTheUrlChanges() {
        Environment environment = environmentService.create(applicationId,
                new EnvironmentRequest("prod", "http://127.0.0.1:1"));
        healthChecker.check(environment);

        Environment updated = environmentService.updateUrl(environment.getId(), workload.baseUrl());

        assertThat(updated.getHealthStatus()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(updated.getHealthDetail()).isNull();
    }

    @Test
    void leavesHealthUnknownWithoutUrl() {
        Environment environment = environmentService.create(applicationId, new EnvironmentRequest("dev", null));

        assertThat(healthChecker.check(environment)).isEqualTo(HealthStatus.UNKNOWN);
    }
}
