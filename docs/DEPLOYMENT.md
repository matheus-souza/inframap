# InfraMap Homelab Deployment Guide

This guide covers deploying InfraMap in a homelab environment using Docker Compose, configuring master keys, backups, reverse proxies with TLS, and automated container updates.

---

## 1. Quick Start with Docker Compose

InfraMap is packaged as a minimal single-binary container image (<30MB) powered by `gcr.io/distroless/static-debian12:nonroot` and PostgreSQL 17.

### Production `docker-compose.yml`

```yaml
services:
  inframap:
    image: ghcr.io/matheus-souza/inframap:latest
    container_name: inframap
    restart: unless-stopped
    ports:
      - "8055:8055"
    environment:
      - DATABASE_URL=postgres://inframap:${POSTGRES_PASSWORD}@postgres:5432/inframap?sslmode=disable
      - INFRAMAP_PORT=8055
      - INFRAMAP_MASTER_KEY=${INFRAMAP_MASTER_KEY}
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
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_prod_data:/var/lib/postgresql/data
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
  postgres_prod_data:
```

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
INFRAMAP_MASTER_KEY=your_exact_32_character_master_key_here
INFRAMAP_PORT=8055
```

> **Important**: `POSTGRES_PASSWORD` is interpolated into a `postgres://` connection URL. If your password contains URL-reserved characters (`@`, `#`, `?`, `/`, `%`), you must percent-encode them (e.g., `p@ss` → `p%40ss`). Alternatively, set `PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` as separate env vars — pgx supports both formats.

---

## 3. Initial Setup & Onboarding

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

## 5. Automated Updates with Watchtower

InfraMap publishes the `:latest` tag on `ghcr.io` for every official release. You can use **Watchtower** to automate container updates:

```yaml
  watchtower:
    image: containrrr/watchtower
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    command: --interval 3600 --cleanup inframap
```

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
