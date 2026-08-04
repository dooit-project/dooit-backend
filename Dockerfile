FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=build/libs/*.jar
ARG APP_VERSION=local
COPY ${JAR_FILE} /app/todolab.jar

LABEL org.opencontainers.image.title="todolab-backend"
LABEL org.opencontainers.image.revision="${APP_VERSION}"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/todolab.jar"]
