# Latensys

Latensys is a bounded Spring Boot monitoring and rate-limiting project for requests handled by **this application**. It records request outcomes in PostgreSQL, keeps short-lived latency samples and rate-limit state in Redis, and serves a dashboard at `http://localhost:8080/`. It does not monitor arbitrary external services.

## Stack and architecture

- Java 17, Spring Boot 3.4.1, Spring Web, JPA, Actuator
- PostgreSQL for request logs and lifetime/windowed queries
- Redis for exact recent latency samples and atomic rate-limit state
- Static HTML, CSS and Chart.js dashboard, polling `/monitor/*` every five seconds
- Optional Slack and Discord webhook alerts

Incoming requests pass through `MonitoringFilter` first, then `RateLimitingFilter`, then controllers. The outer monitor records allowed, rejected, and unavailable requests once, including thrown handler errors. `MonitoringService` reads PostgreSQL for counts and Redis for exact P95 samples. Spring Boot's Redis auto-configuration reads `spring.data.redis.*`; no custom connection factory is needed. JUnit 5 and Mockito are supplied by `spring-boot-starter-test`; PostgreSQL and Redis integration tests use Testcontainers.

## Metrics and failure policy

- **Attempted**: every stored request that is not a dashboard asset, `/monitor/*`, `/actuator/*`, or the error dispatch. **Allowed**, **blocked**, and **unavailable** are mutually exclusive rate-limit decisions. Blocked means HTTP 429; unavailable means rate-limit Redis could not decide and the request received HTTP 503.
- **Successful**: allowed response below 400. **Client errors**: allowed 4xx responses. **Server errors**: allowed 5xx responses. Rejected requests are excluded from application error counts.
- **Error rate**: `(client errors + server errors) / allowed × 100`. **Blocked percentage**: `blocked / attempted × 100`. These dashboard cards and `/monitor/rate-limit` use lifetime PostgreSQL logs for both numerator and denominator; `/monitor/metrics` and `/monitor/errors` retain their existing fields and expose extra counts. A sequence of separate aggregate reads is not a transactionally consistent snapshot during concurrent traffic.
- **Requests per minute**: attempted requests per wall-clock minute, including 429 and rate-limit 503. Trend arrays contain fourteen completed minutes followed by the current partial minute. The `req/sec` card divides the last completed minute by 60; the blocked quick stat shows the current partial minute's count.
- **Endpoint performance**: allowed requests only. P95 is exact nearest rank: sort numeric latency values and select `ceil(0.95 × n) - 1`. An empty sample window returns `null` in the endpoint API. Only 5 and 15 minute windows are accepted; other `/monitor/endpoints?window=` values return HTTP 400.

Redis latency samples use `latency:v2:<uri>` with timestamp scores and unique latency-plus-UUID members. Writes remove samples older than 15 minutes and set a 16 minute key TTL. Reads filter by score, so stale samples never enter a result even when no new request arrives. Exact P95 costs `O(n log n)` time and `O(n)` memory per endpoint query for `n` samples in the selected window; this deliberately favors simple correctness over high-volume aggregation. Old `latency:*` and `rate:*` keys are ignored, not flushed; legacy keys without TTL may need selective cleanup later. The rate limiter uses a Lua script to remove expired entries, count active requests, insert one unique member only when allowed, set a key TTL, and return remaining quota or retry delay in one Redis round trip. It preserves the 50/60 default and the existing `/test/hello` and `/redis/test` overrides.

If Redis cannot enforce a protected request, the request receives structured HTTP 503 and is not passed to the controller. If PostgreSQL request logging or Redis latency storage fails after a valid application response, that response is preserved and telemetry may be lost; the failure is logged without credentials. Dashboard reads still require PostgreSQL and, for P95, Redis. The rate limiter uses the application clock, so large clock skew across instances can affect windows; this project targets one application instance.

## Run locally

Prerequisites: Java 17, Docker with Compose, and network access for the first Maven dependency download. The Maven wrapper supplies Maven 3.9.12.

1. Copy `.env.example` to `.env` and replace `DB_PASSWORD` with a local password. If ports 5432 or 6379 are already in use, set `DB_PORT` or `REDIS_PORT`, and adjust `DB_URL` to match the chosen database port.
2. Start isolated local infrastructure: `docker compose up -d --wait`. Compose reads `.env` automatically. Its PostgreSQL and Redis named volumes persist across ordinary restarts. It does not modify an existing host database; a port conflict causes startup to fail rather than replacing it. Do not run `docker compose down -v` if you want to keep this data.
3. Spring Boot **does not automatically read `.env`**. Export the variables in your shell before starting the app, for example `set -a; source .env; set +a` in Bash or Zsh. Then run `./mvnw spring-boot:run`.
4. Open `http://localhost:8080/` and call `/test/hello` to generate sample traffic.

For a remote PostgreSQL or Redis instance, provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, and optionally `REDIS_PASSWORD` as environment variables. `SLACK_WEBHOOK_URL` and `DISCORD_WEBHOOK_URL` are optional; omit them to disable those destinations. Set `ALERTS_ENABLED=false` to disable all alert delivery. Do not put real secrets in tracked files or command output. The old database credential in Git history must be rotated manually if it was ever used.

## Build and test

Run `./mvnw --batch-mode verify`. CI runs the same command on pushes and pull requests with Java 17. The integration tests launch disposable PostgreSQL and Redis containers and do not use the local Compose database. Docker must be running for those tests. `spring-boot-starter-test` supplies JUnit 5 and Mockito for unit tests; use Mockito for isolated collaborator behavior and Testcontainers for real persistence/Redis behavior.

## Current limitations

- The dashboard, monitoring endpoints, Actuator health/metrics, and test alert endpoints have no authentication. Keep this prototype on a trusted local network; add access control before public deployment.
- The rate limiter identifies clients by remote address, which may represent a reverse proxy. Dashboard and operational routes are excluded.
- The raw request log has no retention policy or migrations; `ddl-auto: update` remains a development-only convenience. Endpoint statistics make multiple queries per URI.
- Global cards are lifetime totals, while endpoint performance uses five or fifteen minute windows. Raw logs and Redis samples have different lifetimes; a PostgreSQL outage can cause incomplete history.
- Alerts trigger during dashboard reads and use an in-memory cooldown. The configured thresholds are not per-service or per-endpoint rules.

See [docs/ROADMAP.md](docs/ROADMAP.md) for milestone status and acceptance criteria.
