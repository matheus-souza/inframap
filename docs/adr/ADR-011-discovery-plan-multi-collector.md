# 11. Discovery Plan — Multi-Collector Sources

Date: 2026-08-29

## Context

Each `DiscoverySource` was locked to a single `type` (e.g., `icmp_sweep` or `proxmox`). In a real homelab network, a single subnet typically hosts heterogeneous infrastructure: bare-metal hosts reachable via ICMP, Proxmox VMs managed by an API, Docker containers behind a socket, and UniFi access points controlled by a separate controller. Users were forced to create N separate sources for the same CIDR — each with its own name, schedule, and lifecycle — to achieve comprehensive coverage. This created friction and fragmented the operational view.

The backend orchestrator already runs all registered network collectors (ICMP, ARP, ReverseDNS, SNMP) concurrently regardless of the source's `type` field. The `type` field primarily controls confidence scoring in the reconciler (`SourceConfidenceMatrix`) and trusted-provider routing (`isTrustedProvider`), not scanner selection. This duality between the declared intent ("I'm an ICMP source") and the actual behavior ("I run everything") was a design smell.

## Decision

Evolve `DiscoverySource` into a **Discovery Plan** that groups multiple collectors under one source entity.

### Data Model

Introduce a normalized child table `discovery_source_collectors`:

```sql
CREATE TABLE discovery_source_collectors (
    id              UUID PRIMARY KEY,
    source_id       UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    collector_type  VARCHAR(64) NOT NULL,
    config_encrypted TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_source_collector UNIQUE (source_id, collector_type)
);
```

Introduce `discovery_collector_runs` for per-collector execution observability:

```sql
CREATE TABLE discovery_collector_runs (
    id              UUID PRIMARY KEY,
    source_id       UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    collector_type  VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    devices_found   INTEGER NOT NULL DEFAULT 0,
    duration_ms     INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Migration Strategy

- **Data migration**: For each existing `discovery_sources` row, insert a corresponding `discovery_source_collectors` row with `collector_type = discovery_sources.type` and `config_encrypted = discovery_sources.config_encrypted`.
- **Column retention**: The `type` column on `discovery_sources` is kept but marked deprecated. New code reads from the child table. The column will be dropped in a future migration after all clients migrate.
- **API backward compatibility**: `POST /api/v1/discovery/sources` accepts both `type: string` (legacy) and `collectors: [{type, config}]` (new). If `type` is provided without `collectors`, the backend creates a single-collector plan automatically.

### Execution Model

The orchestrator iterates over enabled collectors from the child table. Each collector runs independently with its own config. Failures are isolated — a Proxmox API timeout does not abort the ICMP sweep. The source's `last_status` gains a new value `partial` for mixed success/failure outcomes.

### Observability

Each collector execution produces a `discovery_collector_runs` record with status, duration, devices found, and error message. Retention is configurable (default: 7 days) with automatic purge via a background cron job.

### Scope Boundaries

This cycle implements the architecture for network collectors (ICMP, ARP, mDNS) that require no per-collector config. Provider integrations (Proxmox, Docker, UniFi) are rendered as disabled chips labeled "Em breve" in the UI, with full integration planned as the immediate next demand.

## Consequences

- **Good**: Users configure one plan per network instead of N fragmented sources. Operational view is unified. The confidence matrix and reconciler work unchanged because per-collector type identity is preserved in the child table.
- **Good**: Per-collector run history enables targeted troubleshooting ("Proxmox failed because token expired" vs "everything failed").
- **Good**: Zero breaking changes — existing API clients, sources, and data continue working without modification.
- **Bad**: One additional JOIN in source queries (sources → collectors). Mitigated by the small cardinality (~6 collector types max per source).
- **Neutral**: The deprecated `type` column on `discovery_sources` will need a cleanup migration in a future cycle.
- **Neutral**: Provider collector implementations (Proxmox, Docker, UniFi) are deferred, limiting multi-collector value to network sweep types initially.
