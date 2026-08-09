# Stage 1: Build Frontend (Kotlin WASM)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS builder-frontend
WORKDIR /app/frontend
COPY frontend/gradle/ /app/frontend/gradle/
COPY frontend/gradlew /app/frontend/gradlew
COPY frontend/build.gradle.kts /app/frontend/build.gradle.kts
COPY frontend/settings.gradle.kts /app/frontend/settings.gradle.kts
COPY frontend/gradle/libs.versions.toml /app/frontend/gradle/libs.versions.toml
COPY frontend/gradle.properties* /app/frontend/

# Pre-fetch dependencies to cache this layer
RUN ./gradlew dependencies --no-daemon

COPY frontend/src/ /app/frontend/src/

RUN ./gradlew wasmJsBrowserDistribution --no-daemon

# Stage 2: Build Backend Single Binary (Go + Embedded WASM)
FROM golang:1.25-alpine AS builder-backend
ARG APP_VERSION=dev
WORKDIR /app/backend
COPY backend/go.mod backend/go.sum ./
RUN go mod download

COPY backend/ ./
COPY --from=builder-frontend /app/frontend/build/dist/wasmJs/productionExecutable/ ./cmd/api/static/

RUN CGO_ENABLED=0 go build -ldflags="-s -w -X github.com/matheussouza/inframap/modules/configuration/usecase.AppVersion=${APP_VERSION}" -o /inframap ./cmd/api

# Stage 3: Minimal Runtime (Distroless Static)
FROM gcr.io/distroless/static-debian12:nonroot

LABEL org.opencontainers.image.source="https://github.com/matheus-souza/inframap"
LABEL org.opencontainers.image.description="InfraMap — Homelab Infrastructure & Topology Management"
LABEL org.opencontainers.image.licenses="MIT"

WORKDIR /
COPY --from=builder-backend /inframap /inframap

EXPOSE 8055
USER 65532:65532

ENTRYPOINT ["/inframap"]
