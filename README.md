# Latensys

Latensys is a Spring Boot prototype that measures requests handled by **this application**. It records request counts and latency in PostgreSQL, stores short-term counters in Redis, applies per-IP rate limits, and serves a dashboard at `http://localhost:8080/`. It does not yet monitor arbitrary external services.

## Stack and architecture

- Java 17, Spring Boot 3.4.1, Spring Web, JPA, Actuator
- PostgreSQL for request logs and lifetime/windowed queries
- Redis for short-term counters, latency samples, and rate-limit state
- Static HTML, CSS and Chart.js dashboard, polling `/monitor/*` every five seconds
- Optional Slack and Discord webhook alerts

Incoming requests pass through `RateLimitingFilter` and `MonitoringFilter`, then the controllers. `MonitoringService` reads PostgreSQL and Redis for the dashboard. Spring Boot's Redis auto-configuration reads `spring.data.redis.*`; no custom connection factory is needed. JUnit 5 and Mockito are supplied by `spring-boot-starter-test`; PostgreSQL and Redis integration tests use Testcontainers.

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

- P95 is currently incorrect: Redis samples are sorted by timestamp, and duplicate latency values can overwrite one another. This is scheduled for M1.
- Rate-limit check and insert are separate Redis calls, so concurrent requests can exceed the limit; same-millisecond requests can collide. M1 will make this atomic.
- A request that throws through the filter chain may not be recorded. Redis or PostgreSQL errors can affect the request path.
- The dashboard, monitoring endpoints, Actuator health/metrics, and test alert endpoints have no authentication. Keep this prototype on a trusted local network; add access control before public deployment.
- The rate limiter identifies clients by remote address, which may represent a reverse proxy. It also applies to static files and bypasses monitoring routes.
- The raw request log has no retention policy or migrations; `ddl-auto: update` remains a development-only convenience. Endpoint statistics make multiple queries per URI.
- Global metrics are lifetime totals while endpoint charts use five or fifteen minute windows. The dashboard's blocked percentage mixes counts with incompatible denominators.
- Alerts trigger during dashboard reads and use an in-memory cooldown. The configured thresholds are not per-service or per-endpoint rules.

See [docs/ROADMAP.md](docs/ROADMAP.md) for milestone acceptance criteria and M1 regression scenarios.
