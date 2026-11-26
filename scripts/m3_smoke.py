#!/usr/bin/env python3
"""Exercise real Latensys-to-demo HTTP forwarding on isolated local ports."""

import argparse
import getpass
import http.cookiejar
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request


def fetch(opener, url, data=None, content_type=None):
    headers = {"Content-Type": content_type} if content_type else {}
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with opener.open(request, timeout=10) as response:
            return response.status, response.read(), response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers.get("Content-Type", "")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--demo-url", default="http://127.0.0.1:8081")
    parser.add_argument("--admin-username", default=os.environ.get("ADMIN_USERNAME"))
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"))
    args = parser.parse_args()
    if not args.admin_username:
        parser.error("set ADMIN_USERNAME")
    if not args.admin_password:
        args.admin_password = getpass.getpass("Administrator password: ")
    base = args.base_url.rstrip("/")
    demo = args.demo_url.rstrip("/")
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    anonymous_status = fetch(opener, base + "/monitor/metrics")[0]
    assert anonymous_status == 401, f"anonymous metrics returned {anonymous_status}"
    assert json.loads(fetch(opener, base + "/actuator/health")[1]) == {"status": "UP"}
    login_html = fetch(opener, base + "/login")[1].decode("utf-8")
    token = re.search(r'name="_csrf" value="([^"]+)"', login_html)
    assert token, "missing login CSRF token"
    form = urllib.parse.urlencode({"username": args.admin_username,
                                   "password": args.admin_password,
                                   "_csrf": token.group(1)}).encode("utf-8")
    assert fetch(opener, base + "/login", form)[0] == 200

    for _ in range(5):
        status, body, content_type = fetch(opener, base + "/app/hello")
        assert status == 200 and "application/json" in content_type
        assert json.loads(body)["service"] == "independent-demo"
    rejected = fetch(opener, base + "/app/hello")
    assert rejected[0] == 429 and json.loads(rejected[1])["error"] == "rate_limited"
    stats = json.loads(fetch(opener, demo + "/stats")[1])
    assert stats["helloCalls"] == 5, stats

    failure = fetch(opener, base + "/app/fail")
    assert failure[0] == 500 and json.loads(failure[1])["error"] == "demo_failure"
    echo = fetch(opener, base + "/app/echo", b"hello-proxy", "text/plain")
    assert echo[0] == 200 and echo[1] == b"hello-proxy"
    metrics = json.loads(fetch(opener, base + "/monitor/metrics")[1])
    assert (metrics["attemptedRequests"], metrics["allowedRequests"],
            metrics["blockedRequests"], metrics["serverErrors"]) == (8, 7, 1, 1), metrics
    endpoints = json.loads(fetch(opener, base + "/monitor/endpoints?window=5")[1])
    assert "/app/hello" in [row["uri"] for row in endpoints]
    assert fetch(opener, base + "/app/unlisted")[0] == 404
    print(json.dumps({"hello_allowed": 5, "hello_rejected": 1,
                      "upstream_hello_calls": stats["helloCalls"],
                      "upstream_error_status": failure[0], "echo_status": echo[0],
                      "accounting": {key: metrics[key] for key in
                                     ("attemptedRequests", "allowedRequests", "blockedRequests", "serverErrors")},
                      "dashboard_endpoint_visible": True, "unlisted_path_status": 404}, indent=2))


if __name__ == "__main__":
    main()
