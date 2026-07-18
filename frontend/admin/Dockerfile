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

FROM mise-base AS web-build

RUN mise install node pnpm

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml vite.config.ts ./
COPY tooling/patches/ ./tooling/patches/
COPY frontend/packages/core/package.json ./frontend/packages/core/package.json
COPY frontend/admin/package.json ./frontend/admin/package.json

RUN "$(mise which pnpm)" install --frozen-lockfile

COPY frontend/packages/core/ ./frontend/packages/core/
COPY frontend/admin/ ./frontend/admin/

RUN "$(mise which pnpm)" build

FROM nginx:1.30.3-alpine AS web

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=web-build /workspace/frontend/admin/dist/ /usr/share/nginx/html/
