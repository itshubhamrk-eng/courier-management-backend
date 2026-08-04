# =============================================================================
# Multi-stage build for the Courier API.
# Build context is this directory (backend/).
# =============================================================================

# --- Stage 1: build ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependencies are resolved in their own layer so that a source-only change does
# not re-download the whole repository on every build.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root. A container escape should not land on uid 0.
RUN addgroup -S courier && adduser -S courier -G courier

WORKDIR /app
COPY --from=build --chown=courier:courier /build/target/*.jar app.jar

USER courier
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=dev

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then respects whatever the
# orchestrator gives the container instead of guessing.
# ExitOnOutOfMemoryError: a heap-exhausted JVM should die and be restarted, not
# limp along failing every request.

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
