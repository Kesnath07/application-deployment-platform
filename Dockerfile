# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Stage 1: build the Spring Boot jar and split it into cache-friendly layers.
# Tests are executed by the CI pipeline before the image is built, so they are
# skipped here to keep image builds fast and free of test-only infrastructure.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/control-center.jar extract --layers --launcher --destination target/extracted

# ---------------------------------------------------------------------------
# Stage 2: minimal JRE runtime, running as an unprivileged user.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

LABEL org.opencontainers.image.title="cloud-deployment-control-center" \
      org.opencontainers.image.description="Internal developer platform for ECS Fargate deployments"

RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app

WORKDIR /app

# Least-frequently changing layers first so dependency layers stay cached.
# Files remain root-owned (read-only for the app user).
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# Container-aware JVM sizing: the heap follows the ECS task memory limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    PORT=8080

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/api/health"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
