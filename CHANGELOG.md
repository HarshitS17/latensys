# Changelog

## v1.0.0 — 2026-11-26

### Added

- Java 17 Spring Boot API gateway with one fixed, allowlisted upstream and an independent demo application.
- Atomic Redis sliding-window limits, exact recent P95 samples, and PostgreSQL request accounting.
- Flyway migrations, bounded retention cleanup, scheduled alert evaluation, and authenticated dashboard.
- Production multi-stage images and single-VM Compose stack with Caddy HTTPS termination, private data network, persistent stores, and non-root Java containers.
- Explicit trusted-proxy client identity and forwarding-header spoof protection.
- Deployment, architecture, API, demonstration, benchmark, and release documentation.

### Verification

The M3 baseline passed 36 automated tests, real upstream HTTP smoke checks, and local authenticated load checks. M4 verification results are recorded in the final implementation report and release checklist; public deployment is pending.
