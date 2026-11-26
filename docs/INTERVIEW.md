# Technical interview explanation

Latensys is a single-upstream gateway and monitoring dashboard. I built it to show correctness under real HTTP traffic rather than only displaying mock metrics. The request passes through an outer monitoring filter and an atomic Redis Lua limiter before it reaches a fixed-origin, exact-path HTTP proxy. The monitor records allowed, blocked, and Redis-unavailable decisions in PostgreSQL; endpoint P95 comes from unique recent Redis latency samples. A failing Redis limiter returns 503 without forwarding, while telemetry write failure does not mutate a completed upstream response.

I reduced dashboard database work from 118 to 5 statements per refresh in a local 200-log/10-endpoint MockMvc scenario by using grouped queries and one retained-period aggregate. The P95 refresh time changed from 47.373 ms to 10.200 ms in the same local harness; that is a comparison, not a production capacity estimate. For deployment, I packaged Java 17 non-root images in one Compose stack with Caddy HTTPS and private data services. Caddy overwrites the client IP header, and Latensys accepts it only from Caddy's pinned container IP; direct spoofed headers are ignored. The product deliberately supports one administrator and one buffered upstream, and public deployment remains pending.

## Resume bullets

- Built a Java 17/Spring Boot API monitoring gateway with atomic Redis Lua sliding-window limits, exact nearest-rank P95 latency, PostgreSQL/Flyway request accounting, and an authenticated dashboard.
- Reduced local sequential MockMvc dashboard refresh work from 118 to 5 PostgreSQL statements and P95 from 47.373 ms to 10.200 ms on a 200-log/10-endpoint scenario by replacing per-endpoint reads with grouped queries.
- Packaged the application and independent demo in non-root multi-stage Docker images behind Caddy HTTPS on a private Compose network; verified rate-limit behavior and proxy-header trust with real HTTP traffic locally.
