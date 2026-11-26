# Architecture and request flows

The [README diagram](../README.md#architecture) shows the five containers and the internal components. The production `edge` network contains Caddy and Latensys. The `data` network is internal and contains Latensys, PostgreSQL, Redis, and the demo. Only Caddy publishes host ports. Caddy has a pinned `172.30.40.10` address, which Latensys matches before accepting its overwritten `X-Real-IP`. The forwarded-header framework feature stays disabled.

## Allowed request

1. The client connects over HTTPS to Caddy. Caddy replaces client-address headers and forwards to Latensys.
2. `MonitoringFilter` starts timing. `RateLimitingFilter` selects the default or exact-path policy and asks the atomic Redis Lua script to decide using Redis server time.
3. On allowance, `/app/**` validates the exact path, method, body size, and fixed target origin. It makes a finite-time, bounded request to the private upstream.
4. The upstream status and bounded body return to the client. The monitor writes one PostgreSQL log and a unique Redis latency sample. Dashboard queries aggregate durable logs and recent Redis samples.

## Rejected request

The Lua script observes that the current sorted set already contains the maximum number of requests. Latensys returns HTTP 429 with `Retry-After` and does not call upstream. The monitor stores decision `BLOCKED`; blocked requests count as attempts but do not inflate application error rate.

## Upstream failure

An upstream HTTP 5xx remains an HTTP 5xx. Connection failure returns 502 and timeout returns 504. Oversized upstream response returns 502. All are allowed rate-limit attempts and count toward server error metrics. Client-provided destinations, redirects, cookies, Authorization, and arbitrary forwarded headers never enter the upstream request.

## Redis enforcement failure

If Redis cannot decide, the gateway fails closed with HTTP 503 and no upstream call. The monitor records `UNAVAILABLE` if PostgreSQL works. PostgreSQL log failures or Redis latency-storage failures after a completed upstream response are logged without secrets and do not rewrite that response; telemetry may be missing.

## Data and jobs

Flyway creates and evolves the PostgreSQL log schema. The retained-period aggregate, grouped endpoint query, and grouped minute query avoid per-endpoint SQL calls. A bounded scheduled cleanup deletes old logs. Redis stores 16-minute exact latency samples and atomic sliding-window rate-limit sets. A separate scheduled alert evaluator reads the same metrics and sends optional webhook or console alerts with finite HTTP timeouts; it is a single-instance job.
