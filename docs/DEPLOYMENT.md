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
      DATABASE_URL: ${DATABASE_URL:?DATABASE_URL must be set}
      INFRAMAP_PORT: "8055"
      INFRAMAP_MASTER_KEY: ${INFRAMAP_MASTER_KEY:?INFRAMAP_MASTER_KEY must be set}
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
