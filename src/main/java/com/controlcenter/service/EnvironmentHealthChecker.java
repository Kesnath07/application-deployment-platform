package com.controlcenter.service;

import com.controlcenter.config.HealthCheckProperties;
import com.controlcenter.domain.Environment;
import com.controlcenter.domain.HealthStatus;
import com.controlcenter.repository.EnvironmentRepository;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
            ProbeResult result = probe(environment.getUrl());
            environmentService.recordHealth(environment.getId(), result.status(), result.detail());
        }
    }

    public HealthStatus check(Environment environment) {
        ProbeResult result = environment.getUrl() == null
                ? new ProbeResult(HealthStatus.UNKNOWN, null) : probe(environment.getUrl());
        environmentService.recordHealth(environment.getId(), result.status(), result.detail());
        return result.status();
    }

    ProbeResult probe(String baseUrl) {
        String target = baseUrl + properties.path();
        try {
            HttpStatusCode status = restClient.get().uri(target)
                    .retrieve()
                    .onStatus(code -> true, (request, response) -> { })
                    .toBodilessEntity()
                    .getStatusCode();
            if (status.is2xxSuccessful()) {
                return new ProbeResult(HealthStatus.HEALTHY, null);
            }
            HttpStatus known = HttpStatus.resolve(status.value());
            String detail = "HTTP " + status.value() + (known == null ? "" : " " + known.getReasonPhrase());
            log.warn("Health probe to {} returned {}", target, detail);
            return new ProbeResult(HealthStatus.UNHEALTHY, detail);
        } catch (Exception ex) {
            log.warn("Health probe to {} failed: {}", target, ex.getMessage());
            return new ProbeResult(HealthStatus.UNHEALTHY, describe(ex));
        }
    }

    /** Turns a transport failure into a short reason an operator can act on. */
    private String describe(Exception ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return "No response within " + properties.timeout().toMillis() + " ms";
            }
            if (cause instanceof ConnectException) {
                return "Connection refused";
            }
            if (cause instanceof UnknownHostException) {
                return "Unknown host " + cause.getMessage();
            }
        }
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return "Request failed: " + (root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage());
    }

    /** Outcome of one probe; {@code detail} explains a failure and is null for healthy endpoints. */
    record ProbeResult(HealthStatus status, String detail) {
    }
}
