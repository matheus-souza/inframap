# InfraMap Release Process Guide

This document outlines the versioning policy, automated release workflow, and rollback procedures for InfraMap.

---

## 1. Versioning Strategy

InfraMap adheres strictly to **Semantic Versioning (SemVer 2.0.0)**:

- `v{MAJOR}.{MINOR}.{PATCH}` (e.g. `v1.0.0`)
- `v{MAJOR}.{MINOR}.{PATCH}-rc.{N}` (e.g. `v1.0.0-rc.26` for Release Candidates)
  - **MAJOR**: Incompatible API changes or breaking architecture refactors.
  - **MINOR**: Backward-compatible new features (e.g. new integration discovery provider).
  - **PATCH**: Backward-compatible bug fixes and security patches.
  - **RELEASE CANDIDATES (`-rc.N`)**: Pre-releases published to GHCR for validation before stable release. Pre-releases are tagged on GHCR as `:vX.Y.Z-rc.N` (the `:latest` tag is only updated on stable releases).

---

## 2. Automated Release Pipeline

Releases are fully automated via GitHub Actions:

- **Merge to `develop`**: Automatically generates and pushes the next Release Candidate tag (e.g. `v1.0.0-rc.27`), builds multi-arch container images (`:v1.0.0-rc.27`), scans for vulnerabilities via Trivy, and creates a pre-release on GitHub Releases.
- **Merge to `main`**: Automatically generates and pushes the official release tag (e.g. `v1.0.0`), builds multi-arch container images (`:v1.0.0` and `:latest`), scans for vulnerabilities via Trivy, and creates an official release on GitHub Releases.
- **Manual Trigger**: Can also be manually initiated via `make release VERSION=v1.0.0-rc.X` or via GitHub Actions `workflow_dispatch`.

### What GitHub Actions Pipeline Does Automatically

Upon receiving a tag matching `v*.*.*`:

1. **Tag Format Validation**: Validates exact SemVer `vX.Y.Z` or `vX.Y.Z-rc.N` structure.
2. **Multi-Architecture Container Build**: Builds `linux/amd64` and `linux/arm64` container images with Docker Buildx.
3. **Container Registry Tagging**: Tags images on `ghcr.io/matheus-souza/inframap` with `:vX.Y.Z-rc.N` (and updates `:latest` tag only for stable releases).
4. **Vulnerability Scanning**: Runs Trivy security scanner against `CRITICAL` and `HIGH` CVEs.
5. **GitHub Release Generation**: Creates a GitHub Release (marked as Pre-release for `-rc.N` tags) with auto-generated release notes and quick-start guides.

---

## 3. Testing Release Candidates

Before deploying a new version to production homelab nodes:

```bash
# Pull the newly published release candidate container image
docker pull ghcr.io/matheus-souza/inframap:v1.0.0-rc.26

# Verify health status
curl -f http://localhost:8055/api/v1/health
```

---

## 4. Rollback Procedure

If a release candidate exhibits issues in your environment:

1. Update your `docker-compose.yml` image tag to the previous stable release or working RC:
   ```yaml
   image: ghcr.io/matheus-souza/inframap:v1.0.0-rc.25
   ```
2. Restart the stack:
   ```bash
   docker compose up -d
   ```
