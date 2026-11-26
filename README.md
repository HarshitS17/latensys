# Latensys

Latensys is a bounded Spring Boot monitoring and rate-limiting project with a single configured upstream application. Requests to `/app/<allowed-path>` pass through its atomic Redis limiter, reach the separate application, and are recorded in PostgreSQL. The authenticated dashboard is at `http://localhost:8080/`.

## Stack and architecture

- Java 17, Spring Boot 3.4.1, Spring Web, Spring Security, JPA, Actuator
- PostgreSQL for request logs and retained-period/windowed grouped queries; Flyway migrations
- Redis for exact recent latency samples and atomic rate-limit state
- Static HTML, CSS and Chart.js dashboard, polling `/monitor/*` every five seconds
- Scheduled Slack, Discord, and console alerts
- Independent Spring Boot demo application in `demo-app/`

Incoming application requests pass through `MonitoringFilter`, then `RateLimitingFilter`, then the fixed-upstream proxy. The outer monitor records allowed, rejected, and unavailable requests once, including upstream errors. Login, dashboard, monitoring, and Actuator traffic are excluded. `MonitoringService` reads PostgreSQL for counts and Redis for exact P95 samples. PostgreSQL and Redis integration tests use Testcontainers.

## Metrics and failure policy

- **Attempted**: every stored application request, including allowed proxy paths, blocked requests, and unlisted paths. Login, logout, dashboard assets, `/monitor/*`, `/actuator/*`, alert test routes, and error dispatches are excluded. **Allowed**, **blocked**, and **unavailable** are mutually exclusive rate-limit decisions. Blocked means HTTP 429; unavailable means rate-limit Redis could not decide and the request received HTTP 503.
- **Successful**: allowed response below 400. **Client errors**: allowed 4xx responses. **Server errors**: allowed 5xx responses. Rejected requests are excluded from application error counts.
- **Error rate**: `(client errors + server errors) / allowed × 100`. **Blocked percentage**: `blocked / attempted × 100`. Dashboard totals use one PostgreSQL aggregate over the configured retention window (30 days by default), including both numerator and denominator. `/monitor/metrics` and `/monitor/errors` retain their existing fields and expose extra counts. Different HTTP endpoints may observe slightly different instants during concurrent traffic.
- **Requests per minute**: attempted requests per wall-clock minute, including 429 and rate-limit 503. Trend arrays contain fourteen completed minutes followed by the current partial minute. The `req/sec` card divides the last completed minute by 60; the blocked quick stat shows the current partial minute's count.
- **Endpoint performance**: allowed requests only. P95 is exact nearest rank: sort numeric latency values and select `ceil(0.95 × n) - 1`. An empty sample window returns `null` in the endpoint API. Only 5 and 15 minute windows are accepted; other `/monitor/endpoints?window=` values return HTTP 400.

Redis latency samples use `latency:v2:<uri>` with timestamp scores and unique latency-plus-UUID members. Writes remove samples older than 15 minutes and set a 16 minute key TTL. Reads filter by score, so stale samples never enter a result even when no new request arrives. Exact P95 costs `O(n log n)` time and `O(n)` memory per endpoint query for `n` samples in the selected window. The rate limiter uses Redis server time and one atomic Lua script. The default policy is 50 requests per 60 seconds. Exact-path overrides are `/test/hello` (10/60), `/redis/test` (3/60), and `/app/hello` (5/60), all configurable through environment variables. An exact override takes precedence over the default; no wildcard matching is performed. Nonpositive limits or windows fail startup validation.

If Redis cannot enforce a protected request, the request receives structured HTTP 503 and is not passed to the controller. If PostgreSQL request logging or Redis latency storage fails after a valid application response, that response is preserved and telemetry may be lost; the failure is logged without credentials. Dashboard reads still require PostgreSQL and, for P95, Redis. The Redis client has configurable finite command and connection timeouts, defaulting to two seconds.

Alerts are evaluated every minute by a separate non-overlapping scheduled job, not by dashboard GET requests. The existing thresholds remain: error rate >10%, endpoint P95 >1000 ms, last completed minute traffic >1000, and last completed minute blocked requests >50. Error and endpoint P95 alerts require at least 20 allowed samples by default. Failed metric reads cause the evaluation to stop without sending partial alerts. Webhook connection and response timeouts default to two seconds; failed webhook attempts do not start the five-minute cooldown. If no webhook is configured, the console log is the destination. Set `ALERTS_ENABLED=false` to disable the evaluator and delivery.

Request logs are retained for `RETENTION_DAYS` (default 30). The dashboard excludes older rows immediately. A scheduled cleanup deletes at most `RETENTION_BATCH_SIZE × RETENTION_MAX_BATCHES` rows per run (default 50,000) after an initial one-hour delay and then once per hour. PostgreSQL supplies a timestamp index for recent-window and retention scans. The cleanup job can be disabled with `RETENTION_ENABLED=false`; disabled cleanup leaves old rows on disk even though dashboards exclude them.

## Security and upstream integration

One administrator signs in with an externally configured username and BCrypt password hash. Spring Security uses a server-side session and CSRF protection for login, logout, and management actions. The dashboard, `/monitor/*`, `/actuator/metrics`, and development alert test routes require authentication. Unauthenticated monitoring and operational API requests return HTTP 401; browser navigation redirects to `/login`. Among operational endpoints, only `/actuator/health` is public; it exposes status without component details. The login page, its stylesheet, and `/app/**` are also public. The production profile removes `/test/*` controllers and requires `APP_UPSTREAM_URL`; it marks the session cookie Secure, so serve it through HTTPS. Spring does not trust forwarded headers by default. There is no CORS allow-all setting. Login credentials and authentication routes are excluded from request logging and rate-limit keys.

`/app/**` is a public application traffic path, separate from management routes. The proxy uses only `APP_UPSTREAM_URL`, which must be a fixed HTTP(S) origin without credentials, path, query, or fragment. `APP_ALLOWED_PATHS` lists exact routes, with a maximum of 100; unknown paths return 404 and are grouped as `/app/_other` for monitoring and rate limiting. Client supplied URL parameters cannot change the upstream host. Only GET, POST, PUT, PATCH, and DELETE are supported. The proxy copies request body, Content-Type, and Accept; it returns the upstream status, body, and Content-Type. It deliberately does not forward cookies, Authorization, Host, redirects, arbitrary headers, streaming, or WebSockets. Requests and responses are buffered with configurable 64 KiB and 1 MiB defaults. Connection timeout defaults to 2 seconds; response headers and body delivery each have a 5-second timeout by default. Upstream connection failure returns 502; timeout returns 504; oversized bodies return 413 or 502. These proxy outcomes are monitored as allowed application attempts.

To integrate another Spring Boot app, set `APP_UPSTREAM_URL` to its trusted origin and `APP_ALLOWED_PATHS` to the exact routes to expose. Add exact rate-limit overrides in `application.yml` or Spring configuration if those routes need a different policy. Restart Latensys after changing policies. Keep the upstream private so clients cannot bypass Latensys by calling it directly.

## Run locally

Prerequisites: Java 17, Docker with Compose, and network access for the first Maven dependency download. The Maven wrapper supplies Maven 3.9.12.

1. Copy `.env.example` to `.env`. Replace `DB_PASSWORD`, set `ADMIN_USERNAME`, and generate a BCrypt hash with `htpasswd -nBC 12 admin` (enter a private password at the prompt). Put only the hash after `admin:` into `ADMIN_PASSWORD_HASH`, within single quotes. If ports 5432 or 6379 are in use, set `DB_PORT` or `REDIS_PORT`, and adjust `DB_URL`.
2. Start isolated local infrastructure: `docker compose up -d --wait`. Compose reads `.env` automatically. Its PostgreSQL and Redis named volumes persist across ordinary restarts. It does not modify an existing host database; a port conflict causes startup to fail rather than replacing it. Do not run `docker compose down -v` if you want to keep this data.
3. Start the independent demo application in another terminal: `./mvnw -f demo-app/pom.xml spring-boot:run`. It listens on `127.0.0.1:8081`.
4. Spring Boot **does not automatically read `.env`**. Export the variables in a private shell with `set -a; source .env; set +a`, then run `./mvnw spring-boot:run`.
5. Open `http://localhost:8080/`, sign in, and call `curl http://127.0.0.1:8080/app/hello`, `curl http://127.0.0.1:8080/app/fail`, or `curl -H 'Content-Type: text/plain' --data 'hi' http://127.0.0.1:8080/app/echo`. The dashboard will show the real upstream calls. A sixth `/app/hello` request within 60 seconds receives 429 and never reaches the demo app; compare direct `http://127.0.0.1:8081/stats` to confirm its call count.

For a remote PostgreSQL or Redis instance, provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, and optionally `REDIS_PASSWORD` as environment variables. `SLACK_WEBHOOK_URL` and `DISCORD_WEBHOOK_URL` are optional; omit them to disable those destinations. Set `ALERTS_ENABLED=false` to disable all alert delivery. Do not put real secrets in tracked files or command output. The old database credential in Git history must be rotated manually if it was ever used.

### Existing PostgreSQL databases

Fresh empty databases run Flyway V1 and V2 automatically, followed by Hibernate `validate`. **Do not point this release at an existing M0/M1 database before reviewing it.** Flyway intentionally refuses to auto-baseline a nonempty schema.

1. Back up the database. Confirm `api_request_logs` has the V1 columns (`id`, `method`, `uri`, `status`, `response_time`, `timestamp`), with optional M1 `decision`; check that no `flyway_schema_history` exists. If the shape differs, reconcile it before proceeding.
2. With the Flyway CLI installed, export `FLYWAY_URL="$DB_URL"`, `FLYWAY_USER="$DB_USERNAME"`, `FLYWAY_PASSWORD="$DB_PASSWORD"`, and `FLYWAY_BASELINE_VERSION=1` in your private shell. Run `flyway baseline` **once** for that reviewed database. Do not baseline a fresh empty database.
3. Start the app. V2 adds `decision` if absent and creates the timestamp index; Hibernate validates the resulting schema. Check Flyway history and retained row counts after startup.

The manual [Flyway baseline command](https://documentation.red-gate.com/fd/baseline-277578867.html) marks V1 as already represented by the existing table. Migrations never drop, truncate, or recreate that table. Automated tests cover fresh installation and explicit M0/M1 baselines with preserved rows.

## Build and test

Run `./mvnw --batch-mode verify`. CI runs the same command on pushes and pull requests with Java 17. The integration tests launch disposable PostgreSQL and Redis containers and do not use the local Compose database. Docker must be running for those tests. `spring-boot-starter-test` supplies JUnit 5 and Mockito for unit tests; use Mockito for isolated collaborator behavior and Testcontainers for real persistence/Redis behavior.

Run `python3 scripts/load_test.py` against a **local development-profile deployment**. It uses `ADMIN_USERNAME` and prompts privately for the administrator password, signs in with CSRF, then sends normal and multi-endpoint traffic, a 60-request burst, and concurrent dashboard polls. Unique probe paths prevent stale rate-limit keys from affecting the result. It checks expected 200/429 counts and PostgreSQL accounting. The production profile disables its probe routes. See [docs/BENCHMARKS.md](docs/BENCHMARKS.md) for the M2 baseline; M3 smoke commands and outcomes are recorded below.

### M3 verification record

With Java 17 and Docker running, `./mvnw --batch-mode clean verify` passed **36 tests** (0 failures, 0 errors, 0 skipped). `./mvnw -f demo-app/pom.xml package` built the independent application. For the real HTTP check, both jars were started on ports 18080 and 18081 with a disposable Compose PostgreSQL/Redis project, `APP_UPSTREAM_URL=http://127.0.0.1:18081`, and private test-only administrator credentials. Then `python3 scripts/m3_smoke.py --base-url http://127.0.0.1:18080 --demo-url http://127.0.0.1:18081` passed: five real upstream `/hello` calls returned 200, the sixth returned 429 without reaching upstream, `/fail` preserved HTTP 500, `/echo` preserved the body, and the dashboard accounting read attempted 8, allowed 7, blocked 1, server errors 1. An unlisted path returned 404. The isolated containers and volumes were removed afterward.

The authenticated `python3 scripts/load_test.py --base-url http://127.0.0.1:18080` run sent 90 probe requests in 1.246 seconds (72.236 requests/s): 80 HTTP 200, 10 intentional HTTP 429, zero unexpected failures, and exact PostgreSQL accounting deltas of 90 attempted, 80 allowed, 10 blocked. Client-observed P50/P95/P99 were 8.480/15.185/20.039 ms; five dashboard polls made 25 successful calls. These local numbers are scenario checks, not production capacity estimates. JavaScript syntax (`node --check src/main/resources/static/dashboard.js`), Python syntax, and `git diff --check` were also checked.

## Current limitations

- `/app/**` is intentionally public for client traffic. Deploy behind HTTPS and keep PostgreSQL, Redis, and the upstream private. The rate limiter identifies clients by remote address; a reverse proxy may collapse many clients into one identity because forwarded headers are deliberately not trusted.
- The one administrator account is configured at startup; there is no password reset or distributed session store. Rate-limit policies also require a restart.
- Only bounded, buffered HTTP requests are supported through the single upstream. Large payloads, streaming, WebSockets, arbitrary headers, and redirects are outside this integration.
- Recent endpoint charts still compute exact P95 from every Redis sample and can become expensive at high per-endpoint traffic. Raw request logging is synchronous and can add latency; PostgreSQL or Redis failures can cause missing telemetry.
- Global cards cover the retained period, while endpoint charts use five or fifteen minute windows. Alerts are single-process scheduled work; their cooldown is not shared across multiple instances.

See [docs/ROADMAP.md](docs/ROADMAP.md) for milestone status and acceptance criteria.
