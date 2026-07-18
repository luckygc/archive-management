# syntax=docker/dockerfile:1

FROM debian:13-slim AS mise-base

ARG MISE_VERSION=2026.7.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential ca-certificates curl git \
    && rm -rf /var/lib/apt/lists/*

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV MISE_DATA_DIR=/mise \
    MISE_CONFIG_DIR=/mise \
    MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise \
    PATH=/mise/shims:$PATH

RUN curl --proto '=https' --tlsv1.2 -fsSL https://mise.run \
    | MISE_VERSION="${MISE_VERSION}" sh

WORKDIR /workspace

COPY mise.toml ./
RUN mise trust /workspace/mise.toml

FROM mise-base AS server-build

RUN mise install java maven

COPY backend/archive-server/ ./backend/archive-server/
RUN cd backend/archive-server \
    && JAVA_HOME="$(mise where java)" \
        PATH="$(mise where java)/bin:$(mise where maven)/bin:${PATH}" \
        "$(mise which mvn)" --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre AS server

WORKDIR /app

COPY --from=server-build /workspace/backend/archive-server/target/am-0.0.1.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
