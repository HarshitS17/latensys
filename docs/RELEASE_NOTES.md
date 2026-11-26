# GitHub release notes draft — v1.0.0

Latensys v1.0 is a bounded Java 17 API monitoring and rate-limiting portfolio application. A single allowlisted upstream runs behind atomic Redis sliding-window enforcement; PostgreSQL stores request outcomes; Redis stores recent exact P95 latency samples. An authenticated dashboard displays retained-period accounting and recent endpoint trends. The included Spring Boot demo makes the end-to-end flow reproducible.

The release adds multi-stage non-root Java images and a single-VM Docker Compose stack with Caddy HTTPS, private PostgreSQL/Redis/demo networking, persistent volumes, health checks, and a pinned trusted-proxy address. The production profile disables test routes and enables Secure session cookies. Deployment and rollback instructions are in `docs/DEPLOYMENT.md`.

**Measured local context:** on an Apple M2 with Java 17 and Docker, a sequential 200-log/10-endpoint MockMvc dashboard scenario fell from 118 to 5 PostgreSQL statements per refresh and from 47.373 ms to 10.200 ms P95 across 20 refreshes. This is not production throughput. A separate M3 local HTTP scenario returned 80 expected 200s and 10 intentional 429s among 90 requests with no unexpected failures.

M4 final verification passed 38 Java 17 automated tests, both ARM64 image builds, fresh Flyway V1/V2 migrations, and an HTTPS real-HTTP smoke test through local Caddy. Five `/app/hello` calls reached upstream and a sixth with different spoofed forwarding headers returned 429 without another upstream call. The final-code MockMvc rerun measured 5 statements, P50 13.880 ms, and P95 18.972 ms under local host load. Actual dashboard imagery was captured during local browser verification, but the browser policy blocked exporting it as a repository artifact.

Public hosting is pending a user-provided VM and domain. No tag or GitHub release has been created.
