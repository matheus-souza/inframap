# RFC-005 — System Architecture

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official architecture of InfraMap.

Its goal is to ensure the project remains modular, decoupled, testable, and prepared to grow over the years without requiring major architectural refactors.

All new features must respect the principles defined in this document.

---

# Architectural Philosophy

InfraMap adopts a **Modular Monolith** architecture, using **Clean Architecture** and **Hexagonal Architecture** principles within each module.

This architecture was chosen to provide:

- operational simplicity;
- low resource consumption;
- high cohesion;
- low coupling;
- ease of testing;
- ease of maintenance.

The project embraces the **Modular Monolith with a Shared Database** paradigm. Isolation between modules is guaranteed in the application layer (via interfaces, Use Cases, and events) and not by physical database separation. This allows leveraging strong referential integrity (Foreign Keys) from the start.

---

# Architectural Principles

Every architectural decision must prioritize:

- low coupling;
- high cohesion;
- well-defined responsibilities;
- incremental evolution;
- simplicity;
- predictability.

A module must be able to evolve and be tested independently at the application level, while sharing the underlying relational database for absolute data integrity.

---

# General Organization

The architecture will be organized by **Capabilities**.

Capabilities represent business features, not technical details.

Each Capability has:

- API
- Controllers
- DTOs
- Use Cases
- Domain
- Repository Interfaces
- Repository Implementations
- Events
- Configuration
- Tests

---

# Backend Structure

```text
backend/
├── cmd/
│
├── internal/
│   ├── bootstrap/
│   ├── platform/
│   └── shared/
│
├── modules/
│   ├── discovery/
│   ├── inventory/
│   ├── topology/
│   ├── integrations/
│   ├── identity/
│   ├── audit/
│   ├── scheduler/
│   ├── configuration/
│   ├── notifications/
│   ├── observability/
│   └── backup/
```

Each module is completely independent.

---

# Internal Module Organization

Each module must have a structure similar to:

```text
inventory/

├── api/
├── controller/
├── dto/
├── usecase/
├── domain/
├── repository/
├── infrastructure/
├── events/
├── config/
├── public/
└── tests/
```

---

# Public Contracts

Each module publishes only its public API.

Example:

```text
inventory/public/
```

Other modules may depend only on these contracts.

Accessing another module's internal implementations is forbidden.

---

# Architectural Style

Communication follows this flow:

```text
HTTP
    │
    ▼
Controller
    │
    ▼
Use Case
    │
    ▼
Domain
    │
    ▼
Repository Interface
    │
    ▼
Repository
    │
    ▼
Database
```

Each layer has well-defined responsibilities.

---

# Layer Responsibilities

## Controller

Responsible for:

- receiving HTTP requests;
- validating input;
- converting DTOs;
- calling Use Cases;
- assembling HTTP responses.

Does not implement business rules.

---

## Use Cases

Responsible for:

- orchestrating business rules;
- coordinating transactions;
- publishing events;
- using public interfaces of other modules.

Every business rule begins in a Use Case.

---

## Domain

Contains:

- entities;
- value objects;
- pure business rules;
- domain services.

The domain never knows about:

- the database;
- HTTP;
- frameworks;
- infrastructure;
- external integrations.

---

## Repository Interfaces

Define persistence contracts.

Never have an implementation.

---

## Repository

Implement persistence.

Responsible only for data access.

Do not implement business rules.

---

# Communication Between Modules

Dependencies are unidirectional.

A module never directly accesses:

- another module's internal repositories;
- another module's internal entities.

*(Note: Foreign Keys across module tables are explicitly allowed at the database level when they represent legitimate domain relationships and add referential integrity).*

All communication happens through:

- public interfaces;
- internal events.

---

# Dependency Injection

Manual injection will be used initially.

If application composition becomes complex, an established library from the Go ecosystem may be adopted.

Adoption must consider:

- maturity;
- performance;
- simplicity;
- ease of replacement.

---

# DTOs

DTOs will never be shared between layers.

Each layer has its own model.

Official flow:

```text
HTTP

↓

CreateDeviceRequest

↓

CreateDeviceInput

↓

Device

↓

CreateDeviceOutput

↓

CreateDeviceResponse
```

Each object has a specific responsibility.

---

# Domain Services

Domain Services should only exist when:

- a rule is used by multiple Use Cases;
- it represents a purely domain rule;
- it does not depend on infrastructure.

Otherwise, the logic remains in the Use Case itself.

---

# Repositories

Each Aggregate has its own Repository.

There will be no repositories shared between modules.

---

# Transactions

Transactions are coordinated by the Use Cases.

Example:

```text
CreateDeviceUseCase

├── DeviceRepository
└── GroupRepository
```

Repositories remain focused exclusively on persistence.

---

# Integrations

Each integration is independent.

Example:

```text
integrations/

homeassistant/
proxmox/
docker/
mqtt/
unifi/
mikrotik/
```

Each integration implements common interfaces and never accesses the database directly.

---

# Discovery Engine

The Discovery Engine is responsible for consolidating information coming from the integrations.

Flow:

```text
Scanner

↓

Raw Device

↓

Normalizer

↓

Discovery Engine

↓

Matcher

↓

Conflict Resolver

↓

Inventory

↓

Database
```

Each Scanner knows only its own technology.

The Core decides:

- merging;
- deduplication;
- updates;
- creation.

---

# Topology

Topology is an independent Capability.

It does not perform discovery.

It consumes events published by Inventory.

Flow:

```text
Discovery

↓

Inventory updated

↓

DeviceUpdated

↓

Topology

↓

TopologyUpdated
```

Topology can use:

- Inventory;
- LLDP;
- CDP;
- ARP;
- MAC Tables;
- data provided by the user.

Its result is a single graph of the infrastructure.

---

# Scheduler

Scheduler does not implement business rules.

Flow:

```text
Scheduler

↓

Job

↓

Executor

↓

Use Case
```

An internal Worker Pool will be used.

Characteristics:

- configurable quantity;
- configurable limit;
- default value based on the number of available CPUs.

The goal is to limit resource consumption while maintaining high concurrency.

---

# Events

There are three categories of events.

## Domain Events

Represent domain changes.

Examples:

- DeviceCreated
- DeviceUpdated
- DiscoveryFinished

---

## Integration Events

Represent external communication.

Examples:

- MQTT
- Discord
- Webhook

---

## System Events

Represent platform events.

Examples:

- ApplicationStarted
- SchedulerStarted
- BackupCompleted

---

# Error Handling

The application uses typed errors.

Hierarchy:

```text
AppError

├── Validation
├── Conflict
├── NotFound
├── Unauthorized
├── Forbidden
├── Internal
├── ExternalService
├── Timeout
```

The Controller automatically converts these into appropriate HTTP responses.

---

# Configuration

Configuration is modular.

Example:

```go
Config
├── Server
├── Database
├── Discovery
├── Logging
├── Audit
├── Backup
└── Integrations
```

Each module knows only its own configuration.

---

# Feature Flags

The project will have support for Feature Flags.

Initially simple.

Prepared for future evolution.

---

# Observability

The entire architecture will be prepared for integration with OpenTelemetry.

Structured logs will be used initially.

Tracing and metrics can be added without architectural changes.

---

# Extensibility

Architectural priorities:

1. Add new integrations without changing the Core.
2. Allow future extraction of modules.
3. Allow replacement of persistence.
4. Allow evolution of the frontend.

---

# Capability Dependency Graph

```text
                    Discovery
                         │
                         ▼
                    Inventory
                    ├──────┬──────────┐
                    ▼      ▼          ▼
                 Topology  Audit  Observability
                    │
                    ▼
               Notifications

Identity ───────────────┐
Configuration ──────────┼────────► Used by the others
Scheduler ──────────────┘

Integrations
      │
      ▼
Discovery
```

---

# Capability Rules

## Discovery

Responsible only for discovering information.

May depend on:

- Integrations

May not depend on:

- Inventory
- Topology
- Notifications
- Audit

---

## Inventory

Source of truth for devices.

May depend on:

- Discovery

May be used by:

- Topology
- Audit
- Notifications
- Observability

Does not know about these Capabilities.

---

## Topology

Responsible for calculating relationships between devices.

Consumes events from Inventory.

Never performs discovery.

---

## Integrations

Responsible only for communication with external systems.

Never depend on each other.

---

## Scheduler

Executes tasks.

Does not implement business rules.

---

## Identity

Responsible for authentication and authorization.

May be used by any module.

---

## Audit

Consumes events.

Never changes business rules.

---

## Notifications

Consumes events.

Never called directly.

---

## Configuration

Provides configuration.

Never implements business rules.

---

## Observability

Consumes metrics, logs, and events.

Never interferes with application logic.

---

# Mandatory Principles

Every implementation must respect:

- SOLID
- KISS
- DRY
- YAGNI
- Dependency Inversion
- Interface Segregation
- Explicit Dependencies
- Convention over Configuration
- Fail Fast
- Stateless Services
- Idempotency in critical operations

Whenever possible:

- prefer logical immutability for DTOs and domain objects;
- avoid coupling between modules;
- use interfaces for abstractions;
- favor composition over inheritance.

---

# Architectural Decisions

Incompatible changes to this architecture must:

- follow the project's semantic versioning;
- be discussed through an RFC;
- preserve the principles defined in this document whenever possible.
