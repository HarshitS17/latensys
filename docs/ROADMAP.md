# Latensys roadmap

## M0 — Foundation, security, test infrastructure

Externalize PostgreSQL and Redis configuration; remove tracked secrets; provide local Compose infrastructure; isolate integration tests with Testcontainers; run `./mvnw --batch-mode verify` in CI; document current behavior and risks. Acceptance: a fresh checkout can start locally with only a private `.env`, tests never connect to a personal database, and existing dashboard/API contracts stay intact.

## M1 — Correctness of monitoring and rate limiting (implemented)

Exact nearest-rank latency percentiles use unique Redis samples and time-filtered reads. Atomic Redis Lua enforcement preserves the existing policies. Request outcomes, including 429 and rate-limit 503, are stored in PostgreSQL; monitoring write failures leave valid responses intact. The dashboard uses matching lifetime counts for blocked percentage and error rate. The current partial minute is labeled separately.

Regression scenarios covered by unit and container-backed tests:

1. P95 returns the correct value for a known unsorted latency distribution, in both five and fifteen minute windows.
2. Duplicate latency values count as separate requests.
3. Concurrent requests cannot exceed a configured limit.
4. Requests with the same millisecond timestamp are counted separately.
5. Samples and rate-limit entries expire exactly at the selected window boundary.
6. A controller exception still produces a monitoring record with the final response status.
7. Redis and PostgreSQL outages follow a documented policy and do not create an unexplained successful response or hide failures.
8. Allowed, blocked, and total counts use matching windows and denominators.

Acceptance: automated unit and container-backed tests cover these cases; the full Java 17 Maven verification suite passes. A separate repeatable load benchmark remains useful portfolio evidence for M3.

## M2 — Deployable single-service product

Protect dashboard and operational endpoints; disable or restrict test alert routes; add database migrations, indexes, and retention; provide a production deployment example, health checks, and a clear alert policy. Acceptance: one Spring Boot service can run Latensys without checked-in secrets, with documented resource and failure behavior.

## M3 — Portfolio release

Publish architecture and tradeoff notes, a reproducible demo, screenshots, and measured overhead under load. Acceptance: a reviewer can start the project, generate traffic, inspect the dashboard, and verify the key performance claims from the README.

## Later — Multi-service monitoring

Multi-service telemetry is outside the bounded portfolio release. Consider it only after the single-service project is reliable and deployed; it would require authenticated ingestion and service identity.
