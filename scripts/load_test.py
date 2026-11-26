#!/usr/bin/env python3
"""Local Latensys load check using only the Python standard library."""

import argparse
import concurrent.futures
import json
import getpass
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import http.cookiejar
from collections import Counter


def percentile(values, rank):
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[max(0, (len(ordered) * rank + 99) // 100 - 1)], 3)


def request(base, path):
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(base + path, timeout=10) as response:
            response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        error.read()
        status = error.code
    except (urllib.error.URLError, TimeoutError):
        status = 0
    return status, (time.perf_counter() - start) * 1000


def metrics(base):
    with urllib.request.urlopen(base + "/monitor/metrics", timeout=10) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--burst", type=int, default=60)
    parser.add_argument("--concurrency", type=int, default=60)
    parser.add_argument("--admin-username", default=os.environ.get("ADMIN_USERNAME"))
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"))
    args = parser.parse_args()
    if args.burst <= 50 or args.concurrency < 1:
        parser.error("burst must exceed the configured default limit of 50")
    base = args.base_url.rstrip("/")
    if not args.admin_username:
        parser.error("set ADMIN_USERNAME for the local admin session")
    if not args.admin_password:
        args.admin_password = getpass.getpass("Administrator password: ")
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    urllib.request.install_opener(opener)
    with urllib.request.urlopen(base + "/login", timeout=10) as login_page:
        page = login_page.read().decode("utf-8")
    match = re.search(r'name="_csrf" value="([^"]+)"', page)
    if not match:
        parser.error("login page did not provide a CSRF token")
    form = urllib.parse.urlencode({"username": args.admin_username,
                                   "password": args.admin_password,
                                   "_csrf": match.group(1)}).encode("utf-8")
    with urllib.request.urlopen(urllib.request.Request(base + "/login", data=form), timeout=10) as response:
        if response.url.endswith("/login?error"):
            parser.error("administrator login failed")
    run_id = uuid.uuid4().hex
    before = metrics(base)
    stop = threading.Event()
    dashboard = []
    dashboard_paths = ["/monitor/metrics", "/monitor/rate-limit",
                       "/monitor/endpoints?window=5", "/monitor/rpm",
                       "/monitor/blocked-trend"]

    def poll_dashboard():
        while not stop.is_set():
            start = time.perf_counter()
            statuses = [request(base, path)[0] for path in dashboard_paths]
            dashboard.append((statuses, (time.perf_counter() - start) * 1000))
            stop.wait(0.25)

    watcher = threading.Thread(target=poll_dashboard, daemon=True)
    watcher.start()
    start = time.perf_counter()
    normal = []
    for _ in range(10):
        normal.append(request(base, f"/test/load/normal/{run_id}"))
        time.sleep(0.1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        paths = [f"/test/load/multi-{i % 2}/{run_id}" for i in range(20)]
        multi = list(pool.map(lambda path: request(base, path), paths))
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        burst = list(pool.map(lambda _: request(base, f"/test/load/burst/{run_id}"),
                              range(args.burst)))
    elapsed = time.perf_counter() - start
    stop.set()
    watcher.join(timeout=12)
    expected_allowed = 10 + 20 + 50
    expected_blocked = args.burst - 50
    after = metrics(base)
    actual = {key: after[key] - before[key] for key in
              ("attemptedRequests", "allowedRequests", "blockedRequests")}
    all_api = normal + multi + burst
    statuses = Counter(status for status, _ in all_api)
    dashboard_statuses = Counter(status for cycle, _ in dashboard for status in cycle)
    duration = [milliseconds for _, milliseconds in all_api]
    result = {
        "workload": {"normal": 10, "multi_endpoint": 20, "burst": args.burst,
                     "concurrency": args.concurrency, "dashboard_polls": len(dashboard)},
        "client_observed": {
            "elapsed_seconds": round(elapsed, 3),
            "requests_per_second": round(len(all_api) / elapsed, 3),
            "p50_ms": percentile(duration, 50), "p95_ms": percentile(duration, 95),
            "p99_ms": percentile(duration, 99),
            "status_counts": dict(statuses),
            "intentional_429_rate": round(statuses[429] / len(all_api), 4),
            "unexpected_failure_rate": round(sum(count for status, count in statuses.items()
                                                 if status not in (200, 429)) / len(all_api), 4),
            "dashboard_status_counts": dict(dashboard_statuses),
            "dashboard_p50_ms": percentile([ms for _, ms in dashboard], 50),
            "dashboard_p95_ms": percentile([ms for _, ms in dashboard], 95),
        },
        "server_recorded_accounting_delta": actual,
        "expected_accounting_delta": {"attemptedRequests": len(all_api),
                                      "allowedRequests": expected_allowed,
                                      "blockedRequests": expected_blocked},
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    healthy = (statuses[200] == expected_allowed and statuses[429] == expected_blocked
               and actual == result["expected_accounting_delta"]
               and all(status == 200 for status in dashboard_statuses))
    return 0 if healthy else 1


if __name__ == "__main__":
    raise SystemExit(main())
