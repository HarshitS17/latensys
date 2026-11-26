# Latensys roadmap

## M0 — Foundation and test infrastructure

Externalize PostgreSQL and Redis configuration; remove tracked secrets; provide local Compose infrastructure; isolate integration tests with Testcontainers; run `./mvnw --batch-mode verify` in CI; document current behavior and risks. Acceptance: a fresh checkout can start locally with only a private `.env`, tests never connect to a personal database, and existing dashboard/API contracts stay intact.

## M1 — Correctness of monitoring and rate limiting (implemented)

Exact nearest-rank latency percentiles use unique Redis samples and time-filtered reads. Atomic Redis Lua enforcement preserves the existing policies. Request outcomes, including 429 and rate-limit 503, are stored in PostgreSQL; monitoring write failures leave valid responses intact. The dashboard uses matching retained-period counts for blocked percentage and error rate. The current partial minute is labeled separately.

Regression scenarios covered by unit and container-backed tests:

1. P95 returns the correct value for a known unsorted latency distribution, in both five and fifteen minute windows.
2. Duplicate latency values count as separate requests.
3. Concurrent requests cannot exceed a configured limit.
4. Requests with the same millisecond timestamp are counted separately.
5. Samples and rate-limit entries expire exactly at the selected window boundary.
6. A controller exception still produces a monitoring record with the final response status.
7. Redis and PostgreSQL outages follow a documented policy and do not create an unexplained successful response or hide failures.
8. Allowed, blocked, and total counts use matching windows and denominators.

Acceptance: automated unit and container-backed tests cover these cases; the full Java 17 Maven verification suite passes. M2 adds a repeatable load benchmark.

## M2 — Performance and operational reliability (implemented)

Grouped endpoint and minute queries make dashboard database query count independent of endpoint count. One aggregate query supplies retained-period accounting. Flyway manages schema changes with manual baselining for reviewed existing databases; bounded scheduled cleanup defaults to 30-day retention. Scheduled alert evaluation is independent of dashboard reads and uses finite webhook timeouts. Redis server time drives atomic rate-limit decisions. A reproducible local load script verifies allowed and rejected counts. Acceptance: Java 17 verification passes, migrations and retention are tested, and measured benchmark results are recorded in `docs/BENCHMARKS.md`.

## M3 — Security and practical integration (implemented)

Single-administrator Spring Security sessions protect the dashboard and management APIs. The production profile disables test controllers. Validated exact-path rate policies replace hardcoded endpoint rules. A fixed, allowlisted, bounded HTTP proxy monitors and rate-limits requests to an independent Spring Boot demo app. Acceptance: Java 17 verification, real HTTP smoke testing, and an authenticated local load run pass; a reviewer can start both applications, send real upstream traffic, and inspect the dashboard.

## M4 — Final portfolio release (implemented locally; public deployment pending)

Multi-stage Java 17 images, non-root processes, health checks, and a production Compose stack package Latensys, the demo, PostgreSQL, Redis, and Caddy. Only Caddy publishes host ports. The production profile requires a pinned trusted proxy address and Secure session cookies; Caddy sanitizes forwarding headers. Deployment, architecture, API, demonstration, and release documentation are included. Local verification results and any remaining gaps are reported separately; a public VM deployment requires the owner's resources and authorization.

## Beyond v1.0 scope

Multi-service monitoring and enterprise SaaS features are not planned milestones for this bounded release.
