# Multi-stage build for gifiti-backend.
# Cite: devops-conventions.md § Dockerfile Baseline
#
# Why -DskipTests in the build stage: tests run separately in CI
# (.github/workflows/ci.yml). Re-running them inside the image build would
# duplicate work and require Testcontainers/Docker-in-Docker.
#
# Healthcheck tool choice: BusyBox `wget` ships with eclipse-temurin:*-alpine
# images by default, so we use it instead of installing `curl`. `--spider`
# means HEAD-style fetch (no body download); `-q` keeps logs clean; non-2xx
# exits non-zero, which Docker maps to "unhealthy".

# ---- build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# Create non-root user (per § Dockerfile Baseline: never run as root).
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R app:app /app

USER app

EXPOSE 8080

# Spring Boot Actuator health endpoint must be reachable on the same port the
# app listens on. start-period gives the JVM headroom to warm up before Docker
# starts counting failures against the unhealthy threshold.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
