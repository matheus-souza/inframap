# Project Foundation

> Status: Living Document
>
> This document defines the architectural foundations and technical principles that guide the InfraMap project.
>
> It intentionally avoids implementation details and version-specific information. Those belong to the source code and build configuration.

---

# Purpose

InfraMap is an open-source, self-hosted platform for infrastructure discovery, inventory, and visualization.

Its primary goal is to become the **Single Source of Truth** for homelabs and self-hosted environments by automatically discovering infrastructure assets, organizing their relationships, and providing a centralized management interface.

The project is intended to be modular, extensible, lightweight, and easy to deploy.

---

# Project Philosophy

InfraMap is built around a small set of principles that influence every architectural decision.

## Simplicity First

Every solution should be as simple as possible while remaining maintainable.

Complexity should only be introduced when it provides measurable value.

---

## Performance by Design

Performance is considered during architectural decisions rather than being treated as an optimization after development.

The project should prioritize:

- Low memory consumption
- Fast startup time
- Low CPU utilization
- Efficient network communication

---

## Docker First

Every official deployment of InfraMap must execute inside containers.

Development, testing and production environments should remain as similar as possible.

No installation should require manual dependency management on the host operating system.

---

## API First

Business rules belong to the backend.

The frontend should only be responsible for presentation and user interaction.

Every feature must first exist as an API capability.

---

## Discovery First

Infrastructure information should be automatically discovered whenever technically possible.

Manual configuration should complement discovery rather than replace it.

---

## Infrastructure as Code

Everything required to build, test and deploy InfraMap must be version controlled.

Examples include:

- Docker configuration
- CI/CD pipelines
- Database migrations
- Build scripts
- Environment templates

---

## Open Standards

Whenever possible, InfraMap should adopt open protocols and widely accepted standards.

Vendor lock-in should be avoided.

---

## Extensibility

Every major subsystem should be designed to support future integrations without requiring architectural changes.

New discovery providers and integrations should be added as independent modules.

---

# Technical Principles

The project follows a modern software architecture focused on long-term maintainability.

## Backend

The backend should be implemented using a compiled language optimized for networking and concurrent workloads.

The project should always target the latest stable or Long-Term Support (LTS) release officially supported by the ecosystem and validated by the project.

---

## Database

A relational database management system will be adopted as the primary persistence layer.

The chosen database must provide:

- ACID transactions
- Strong consistency
- Advanced indexing
- High reliability
- Excellent tooling
- Long-term community support

---

## Frontend

The frontend should be built using a modern declarative UI framework.

The user interface must follow a consistent Design System and prioritize accessibility, responsiveness, and usability.

---

## User Experience

Infrastructure management should remain intuitive regardless of the number of managed assets.

The interface should minimize cognitive load while maximizing visibility.

---

# Deployment Philosophy

InfraMap should be deployable in minutes.

An installation should require only:

- Docker
- Docker Compose (or compatible orchestrator)

No additional manual configuration should be mandatory.

---

# Documentation

Documentation is treated as part of the source code.

Every architectural decision should be documented.

Project documentation must evolve together with the implementation.

---

# Testing

Testing is a fundamental part of the development process.

The project should include:

- Unit Tests
- Integration Tests
- End-to-End Tests

Automated testing should be executed before every release.

---

# Continuous Integration

Every code change should be automatically validated.

Validation includes:

- Formatting
- Static Analysis
- Security Checks
- Automated Tests
- Build Validation

---

# Versioning

The project follows Semantic Versioning.

Release candidates should be published before every stable release.

Stable releases should always represent production-ready versions.

---

# Licensing

InfraMap is distributed under the Apache License 2.0.

The project name, logo, and visual identity may be protected independently from the source code license.

---

# Long-Term Vision

InfraMap is not intended to be only a device inventory.

Its long-term objective is to become a complete infrastructure management platform capable of discovering, documenting, organizing, relating, and visualizing every component of a self-hosted environment.

Every new feature should contribute to one or more of these objectives:

- Discover
- Identify
- Organize
- Relate
- Visualize
- Document
- Manage

Features that do not support this vision should be carefully evaluated before being incorporated into the project.