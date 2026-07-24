# InfraMap — Domain Context & Ubiquitous Language

> **Shared Domain Vocabulary**: This document establishes the ubiquitous language used across all code, tests, and documentation within the InfraMap codebase.

---

## Core Domain Terms

| Term | Definition | Code Representation |
|---|---|---|
| **InfraMap** | Open-source network infrastructure discovery, mapping, and observability platform. | Repository root (`github.com/matheussouza/inframap`) |
| **Node** | Physical, virtual, or cloud infrastructure entity (e.g., Server, Switch, Router, VM, Container). | `Table: nodes`, `struct Node` |
| **Interface** | Physical or logical network interface attached to a Node (e.g., eth0, vlan10). | `Table: interfaces`, `struct Interface` |
| **Link** | Point-to-point connection between two interfaces on separate nodes. | `Table: links`, `struct Link` |
| **Topology** | Graph representation of Nodes, Interfaces, and Links forming the network map. | Domain package: `internal/domain/topology` |
| **Discovery Engine** | Subsystem responsible for scanning IP ranges, executing plugins, and ingesting assets. | Domain package: `internal/domain/discovery` |
| **Discovery Source** | Configuration targeting a range, subnet, or provider API for automated discovery. | `Table: discovery_sources` |
| **Discovery Record** | Timestamped raw observation output produced by a discovery scan. | `Table: device_discovery_records` |
| **Credential** | Encrypted authentication secret used by discovery collectors (SNMP, SSH, API token). | `Table: credentials` |

---

## Architectural Decision Records (ADRs) & Specifications

- **RFC-001**: System Vision & Technical Architecture Specification
- **RFC-006**: Core Domain Models & PostgreSQL Schema Definition
- **RFC-008**: Discovery Engine & Collector Plugin Architecture
- **RFC-010**: Repository Scaffolding & Developer Environment

All active architecture decisions and technical specifications live in `docs/` and `docs/adr/`.
