# InfraMap Roadmap

This document outlines the planned development phases and future goals for InfraMap.

## Phase 1: Foundation (Completed)
- [x] Initial Architectural RFCs
- [x] Data Model & Database Schema Definition
- [x] Event Bus & SDK Contracts
- [x] Visual Identity & Design System

## Phase 2: Scaffolding (Current)
- [ ] Repository Layout Initialization
- [ ] `Makefile` and `docker-compose` setup
- [ ] Core Database Migrations (Goose) & `sqlc` Configuration
- [ ] CI/CD Pipeline (GitHub Actions)

## Phase 3: Core Backend
- [ ] `platform/eventbus`: In-memory event routing
- [ ] `inventory` module: Device and asset CRUD
- [ ] `discovery` module: Engine for continuous state reconciliation
- [ ] Realtime Gateway (SSE)

## Phase 4: Integrations
- [ ] Proxmox VE Provider
- [ ] UniFi Controller Provider
- [ ] Docker Engine Provider

## Phase 5: Frontend
- [ ] Compose Multiplatform (WASM) Boilerplate
- [ ] Design System Component Library
- [ ] Dashboard & Device Listing
- [ ] Interactive Topology Map

## Future Goals
- SNMP Fallback Discovery
- Webhooks & Alerting
- Advanced RBAC UI
