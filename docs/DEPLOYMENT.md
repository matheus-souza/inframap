# InfraMap Homelab Deployment Guide

This guide covers deploying InfraMap in a homelab environment using Docker Compose, configuring master keys, backups, reverse proxies with TLS, and automated container updates.

---

## 1. Quick Start with Docker Compose

InfraMap is packaged as a minimal single-binary container image (<30MB) powered by `gcr.io/distroless/static-debian12:nonroot` and PostgreSQL 17.

### Production `docker-compose.yml`

```yaml
services:
  inframap:
    image: ghcr.io/matheus-souza/inframap:${INFRAMAP_TAG:-latest}
    container_name: inframap
    restart: unless-stopped
    ports:
      - "8055:8055"
    environment:
      DATABASE_URL: ${DATABASE_URL:?DATABASE_URL must be set}
      INFRAMAP_PORT: "8055"
      INFRAMAP_MASTER_KEY: ${INFRAMAP_MASTER_KEY:?INFRAMAP_MASTER_KEY must be set}
    # Lets the image's non-root user send ICMP echo requests over an unprivileged socket,
    # which ICMP sweeps need (docs/DEPLOYMENT.md, "ICMP Sweeps"). The range stays within the
    # 65 536 IDs a user namespace usually maps (unprivileged LXC, rootless Docker).
    sysctls:
      net.ipv4.ping_group_range: "0 65535"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - inframap-net

  postgres:
    image: postgres:17-alpine
    container_name: inframap-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: inframap
      POSTGRES_USER: inframap
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
    volumes:
      - ${POSTGRES_DATA_PATH:-inframap-pgdata}:/var/lib/postgresql/data
    networks:
      - inframap-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U inframap"]
      interval: 5s
      timeout: 5s
      retries: 5

networks:
  inframap-net:
    driver: bridge

volumes:
  inframap-pgdata:
```

### ICMP Sweeps (`ping_group_range`)

The image runs as the non-root user `65532:65532` and ships no `ping` binary, so the ICMP sweep sends its echo requests over an **unprivileged ICMP socket**. Linux opens that socket only for a process whose group is within `net.ipv4.ping_group_range`, a per-network-namespace sysctl. Docker Engine 20.10 and later already widen it for containers with their own network, but older engines, other container runtimes and hardened hosts can leave the kernel default `1 0`, which admits no group at all. The `sysctls` entry above sets the range explicitly, so ICMP sweeps do not depend on the engine's defaults.

Without it, every ICMP sweep is recorded as `skipped` with the message *"ICMP unavailable: unprivileged ICMP sockets are not permitted for this process (net.ipv4.ping_group_range), raw sockets need CAP_NET_RAW, and no ping binary is installed"*, and the source ends `partial` (or `error` when nothing else in the run succeeded) instead of reporting a sweep that never happened as a success.

Check the value the running container sees (expect `0	65535`):

```bash
docker run --rm --network container:inframap busybox cat /proc/sys/net/ipv4/ping_group_range
```

- **Alternative — raw sockets (`CAP_NET_RAW`)**: Docker hands added capabilities only to the container's root user, so `cap_add: [NET_RAW]` alone does nothing for the image's non-root user. It works together with `user: "0:0"`, which gives up the non-root hardening; prefer the sysctl.
- **User namespaces (unprivileged LXC, rootless Docker)**: the kernel accepts only group IDs mapped in the container's user namespace, so a range beyond the mapping fails container start with `failed to write sysctl net.ipv4.ping_group_range ... invalid argument`. Such a namespace usually maps 65 536 IDs (`cat /proc/self/uid_map` on the Docker host shows e.g. `0 100000 65536`), which is why the range above ends at `65535`; it still covers the image's group `65532`. Docker's own default (`0 2147483647`) cannot be applied there either, which is how ICMP sweeps end up without a socket on such hosts.
- **`network_mode: host`**: Docker rejects `net.*` sysctls in the host's network namespace, and the host's own value applies. Set it on the host instead (`sysctl -w net.ipv4.ping_group_range="0 65535"`, persisted in a file under `/etc/sysctl.d/`).

### Image Tags

Every published image carries its exact version tag; `:latest` is reserved for stable releases.

| Tag | Published when | Channel |
|---|---|---|
| `vX.Y.Z-rc.N` | Every merge into `develop` | Pre-release (release candidate) |
| `vX.Y.Z` | Every merge into `main` | Stable |
| `latest` | Every merge into `main` only | Stable — **never** moves for release candidates |

> **Important**: while the project only ships release candidates, `:latest` does not exist on GHCR and `docker compose pull` fails with `manifest unknown`. Set `INFRAMAP_TAG` to the release you want to run (see section 2). Pinning an exact tag is recommended in any case: upgrades become an explicit, reversible step instead of whatever `:latest` points to on the next restart.

Available versions are listed on the [Releases page](https://github.com/matheus-souza/inframap/releases) and in the [container registry](https://github.com/matheus-souza/inframap/pkgs/container/inframap). A tag whose release run failed its Trivy scan may still exist on GHCR without a GitHub Release — deploy only versions that have a Release entry.

---

## 2. Environment Variables & Master Key Generation

### Master Key Generation

InfraMap uses AES-GCM encryption for storing sensitive credentials (e.g. Proxmox tokens, SSH keys). The master key **MUST** be exactly 32 bytes (32 ASCII characters).

Generate a secure 32-character key using OpenSSL or `/dev/urandom`:

```bash
# Option 1: OpenSSL base64 trimmed to 32 chars
openssl rand -base64 32 | cut -c 1-32

# Option 2: tr alphanumeric 32 chars
LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32; echo
```

Create a `.env` file next to your `docker-compose.yml`:

```env
POSTGRES_PASSWORD=your_secure_postgres_password_here
DATABASE_URL=postgres://inframap:your_secure_postgres_password_here@postgres:5432/inframap?sslmode=disable
INFRAMAP_MASTER_KEY=your_exact_32_character_master_key_here

# Image version to run (default: latest — stable releases only, see "Image Tags")
INFRAMAP_TAG=v1.0.0-rc.92

# Optional: bind-mount PostgreSQL data to a specific host path (default: named Docker volume)
# POSTGRES_DATA_PATH=/path/to/persistent/storage/inframap/postgres
```

> **Important**: `DATABASE_URL` and `POSTGRES_PASSWORD` are separate variables. `POSTGRES_PASSWORD` sets the raw password that PostgreSQL stores; `DATABASE_URL` is the connection string the application uses. If your password contains URL-reserved characters (`@`, `#`, `?`, `/`, `%`), percent-encode them only in `DATABASE_URL` (e.g., `p@ss` → `p%40ss` in the URL, but keep `p@ss` as-is in `POSTGRES_PASSWORD`). Alternatively, set `PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` as separate env vars — pgx supports both formats.

---

## 3. Initial Setup & Onboarding

Database migrations are applied automatically on container startup (via embedded Goose). No manual migration step is required.

1. Start the stack: `docker compose up -d`
2. Open `http://<your-server-ip>:8055` in your browser.
3. Complete the Onboarding Wizard to set up admin credentials and start automated network discovery.

---

## 4. Reverse Proxy & TLS Setup

For secure HTTPS access in your homelab, place a reverse proxy like **Caddy** or **Traefik** in front of port `8055`.

### Caddy Example (`Caddyfile`)

```caddy
inframap.homelab.local {
    reverse_proxy localhost:8055
}
```

---

## 5. Updating InfraMap

### Manual Update (any channel)

Migrations run automatically on startup, so take a backup first (see section 6), then point `INFRAMAP_TAG` at the new version:

```bash
docker exec -t inframap-postgres pg_dump -U inframap inframap > inframap_backup_$(date +%F).sql
sed -i 's/^INFRAMAP_TAG=.*/INFRAMAP_TAG=v1.0.0-rc.92/' .env
docker compose pull inframap && docker compose up -d inframap
docker inspect inframap --format '{{.Config.Image}}'   # confirm the running version
curl -s http://localhost:8055/api/v1/health            # expect "status":"ok"
```

To roll back, restore the previous `INFRAMAP_TAG` and the backup taken before the upgrade.

### Automated Updates with Watchtower (stable channel only)

InfraMap moves the `:latest` tag on `ghcr.io` for every stable release. With `INFRAMAP_TAG` unset (or set to `latest`), you can use **Watchtower** to automate container updates:

```yaml
  watchtower:
    image: containrrr/watchtower
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: --interval 3600 --cleanup inframap
```

Watchtower only follows the tag the container already runs: it never picks up release candidates, and a container pinned to an exact version (`INFRAMAP_TAG=vX.Y.Z-rc.N`) is never updated by it. Release candidates are always upgraded manually.

---

## 6. Database Backup & Restore

### Backup

```bash
docker exec -t inframap-postgres pg_dump -U inframap inframap > inframap_backup_$(date +%F).sql
```

### Restore

```bash
cat inframap_backup_2026-08-06.sql | docker exec -i inframap-postgres psql -U inframap -d inframap
```

---

## 7. Monitoring & Health Status

InfraMap provides a health endpoint for uptime checks:

- **Endpoint**: `GET /api/v1/health`
- **Response**:
  ```json
  {
    "status": "ok",
    "timestamp": "2026-08-06T10:00:00Z",
    "services": {
      "database": "up"
    }
  }
  ```
