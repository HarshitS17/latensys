# Local benchmark results

Measured on 2026-11-26 (Asia/Kolkata) on a MacBook Air Mac14,2 (Apple M2, 8 cores, 16 GB RAM), Docker Engine 29.9.3 (8 CPUs, about 8.3 GB assigned), Java 17.0.19, Spring Boot 3.4.1, PostgreSQL 16.6 Testcontainer, and Redis 7.4.1 Testcontainer. These are local comparison measurements, not production capacity guarantees.

## Dashboard before and after M2

Both measurements seeded 200 request logs across 10 endpoints, ran three warmup refreshes, then timed 20 sequential refreshes through MockMvc on one client thread. Hibernate's prepared-statement counter counted PostgreSQL statements. The elapsed time includes all MockMvc calls and application work, but excludes a browser, network, and chart rendering.

| Version | GETs per refresh | PostgreSQL statements per refresh | P50 refresh | P95 refresh |
| --- | ---: | ---: | ---: | ---: |
| M1 baseline | 9 | 118 | 32.392 ms | 47.373 ms |
| M2 optimized | 5 | 5 | 8.986 ms | 10.200 ms |

The M1 refresh requested metrics, errors, rate-limit, endpoints, RPM twice, and blocked trend three times. Its query count was `98 + 2 × endpoint_count`; the ten-endpoint run measured 118. M2 fetches metrics, rate-limit, endpoints, RPM, and blocked trend once each. The M2 integration test asserts five PostgreSQL statements per refresh and separately checks that endpoint query count stays at one with one or ten endpoints. The time comparison includes the change in HTTP call count and is sensitive to host load.

The M1 baseline was captured before M2 edits. The harness is preserved in `scripts/benchmark/M1DashboardBaselineTest.java`. From the current repository, rerun it in an isolated export of M1 commit `3bc1933`:

```sh
baseline_dir=$(mktemp -d)
git archive 3bc1933 | tar -x -C "$baseline_dir"
cp scripts/benchmark/M1DashboardBaselineTest.java "$baseline_dir/src/test/java/com/harshit/monitoring/"
(cd "$baseline_dir" && JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./mvnw --batch-mode -Dtest=M1DashboardBaselineTest test)
```

A rerun with the archived harness measured 118 statements, P50 33.324 ms, and P95 47.937 ms. To reproduce the optimized measurement in the current tree, run:

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./mvnw --batch-mode -Dtest=LatensysApplicationTests#dashboardOptimizedMeasurement test
```

Use any Java 17 installation by changing `JAVA_HOME`; Docker must be running. The test prints `OPTIMIZED query_count_per_refresh=... p50_ms=... p95_ms=...`. Both harnesses use the same seed size, warmup count, measurement count, and sequential MockMvc timing; their request sets reflect the actual dashboard behavior of each version.

## Query plans

`dashboardOptimizedMeasurement` prints `EXPLAIN (ANALYZE, BUFFERS)` for the grouped endpoint query, grouped minute query, and retention lookup. With only 200 seeded rows, PostgreSQL chose sequential scans: endpoint `GroupAggregate` executed in 0.111 ms; minute `HashAggregate` in 0.073 ms; retention lookup in 0.045 ms in the final run. The timestamp index exists for larger tables, but this small sample does not demonstrate an index benefit. Actual plans and timings depend on table size, data distribution, and PostgreSQL statistics.

## HTTP load scenario

The dependency-free script `python3 scripts/load_test.py --base-url http://127.0.0.1:8080` sends 10 paced requests, 20 requests split across two endpoint paths, and a 60-request concurrent burst against unique probe paths. It polls the five dashboard APIs concurrently. The default rate limit permits 50 requests per minute per path and client, so the expected burst outcome is 50 allowed and 10 intentional HTTP 429 responses. The script verifies both status totals and the PostgreSQL accounting delta, then exits nonzero on a mismatch. Use an isolated local deployment with default rate-limit settings and no unrelated traffic.

In the final-code local run against a disposable Compose PostgreSQL/Redis project and the Spring Boot jar, the 90 API requests completed in 1.255 s (71.685 requests/s). Client-observed API latency was P50 6.434 ms, P95 16.683 ms, and P99 42.676 ms. Results were 80 HTTP 200, 10 intentional HTTP 429, and zero unexpected failures. Five dashboard polls made 25 successful HTTP 200 calls, with complete-poll P50 19.646 ms and P95 46.493 ms. PostgreSQL deltas exactly matched attempted 90, allowed 80, blocked 10. Client-observed latency includes HTTP and local network overhead; it is not the server-side `response_time` stored by Latensys.

The workload is short, runs on one host, and uses one service instance. It checks accounting, rate limiting, and dashboard availability under a modest burst; it does not establish sustained throughput, tail latency at scale, multi-instance correctness, or production sizing.

## M4 v1.0 local rerun

The final Java 17 `clean verify` run on 2026-11-26 reran the optimized sequential MockMvc dashboard measurement with the same 200 seeded logs, 10 endpoints, three warmups, and 20 refreshes. It measured **5 PostgreSQL statements per refresh**, P50 **13.880 ms**, and P95 **18.972 ms**. This run was slower than the earlier M2 optimized 10.200 ms P95 measurement on the same host; concurrent Docker image builds and local stack activity make host load a plausible contributor, but no cause was isolated. The repeat confirms the query-count reduction, not a guaranteed latency target. It still excludes browser rendering and network time.
