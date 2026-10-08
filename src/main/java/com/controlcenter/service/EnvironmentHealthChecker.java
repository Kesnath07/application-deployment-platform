package com.controlcenter.service;

import com.controlcenter.config.HealthCheckProperties;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.repository.EnvironmentRepository;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Probes the health endpoint of every environment that has a URL configured and
 * records the result. This gives the dashboard a basic, CloudWatch-independent view
 * of whether each deployed workload is reachable through its load balancer.
 */
@Component
public class EnvironmentHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentHealthChecker.class);

    private final EnvironmentRepository environments;
    private final EnvironmentService environmentService;
    private final HealthCheckProperties properties;
    private final RestClient restClient;

    public EnvironmentHealthChecker(EnvironmentRepository environments, EnvironmentService environmentService,
                                    HealthCheckProperties properties, RestClient.Builder restClientBuilder) {
        this.environments = environments;
        this.environmentService = environmentService;
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Scheduled(initialDelayString = "${control-center.health-check.initial-delay:PT15S}",
            fixedDelayString = "${control-center.health-check.interval:PT60S}")
    public void checkAll() {
        // Load targets in a short read; HTTP calls happen outside of any transaction.
        for (Environment environment : environments.findByUrlIsNotNull()) {
            environmentService.recordHealth(environment.getId(), probe(environment.getUrl()));
        }
    }

    public HealthStatus check(Environment environment) {
        HealthStatus status = environment.getUrl() == null ? HealthStatus.UNKNOWN : probe(environment.getUrl());
        environmentService.recordHealth(environment.getId(), status);
        return status;
    }

    HealthStatus probe(String baseUrl) {
        String target = baseUrl + properties.path();
        try {
            boolean healthy = restClient.get().uri(target)
                    .retrieve()
                    .onStatus(status -> true, (request, response) -> { })
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
            return healthy ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
        } catch (Exception ex) {
            log.warn("Health probe to {} failed: {}", target, ex.getMessage());
            return HealthStatus.UNHEALTHY;
        }
    }
}
