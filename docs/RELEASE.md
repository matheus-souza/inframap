# InfraMap Release Process Guide

This document outlines the versioning policy, automated release workflow, and rollback procedures for InfraMap.

---

## 1. Versioning Strategy

InfraMap adheres strictly to **Semantic Versioning (SemVer 2.0.0)**:

- `v{MAJOR}.{MINOR}.{PATCH}` (e.g. `v0.1.0`)
  - **MAJOR**: Incompatible API changes or breaking architecture refactors.
  - **MINOR**: Backward-compatible new features (e.g. new integration discovery provider).
  - **PATCH**: Backward-compatible bug fixes and security patches.

---

## 2. Triggering an Automated Release

To initiate a release:

```bash
# Ensure you are on develop with all PRs merged and quality gates passing
git checkout develop
git pull origin develop

# Create and push the release tag (e.g. v0.1.0)
make release VERSION=v0.1.0
```

### What GitHub Actions Pipeline Does Automatically

Upon receiving a tag matching `v*.*.*`:

1. **Tag Format Validation**: Validates exact SemVer `vX.Y.Z` structure.
2. **Multi-Architecture Container Build**: Builds `linux/amd64` and `linux/arm64` container images with Docker Buildx.
3. **Container Registry Tagging**: Tags images on `ghcr.io/matheus-souza/inframap` with `:v0.1.0`, `:v0.1`, and `:latest`.
4. **Vulnerability Scanning**: Runs Trivy security scanner against `CRITICAL` and `HIGH` CVEs.
5. **GitHub Release Generation**: Creates a GitHub Release with auto-generated release notes and installation instructions.

---

## 3. Testing Release Candidates

Before deploying a new version to production homelab nodes:

```bash
# Pull the newly published container image
docker pull ghcr.io/matheus-souza/inframap:v0.1.0

# Verify health status
curl -f http://localhost:8055/api/v1/health
```

---

## 4. Rollback Procedure

If a release candidate exhibits issues in your environment:

1. Update your `docker-compose.yml` image tag to the previous stable release:
   ```yaml
   image: ghcr.io/matheus-souza/inframap:v0.0.9
   ```
2. Restart the stack:
   ```bash
   docker compose up -d
   ```
