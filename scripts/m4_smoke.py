#!/usr/bin/env python3
"""Exercise local production HTTPS, session login, upstream, and spoof-resistant limits."""
import argparse
import getpass
import http.cookiejar
import json
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request


def fetch(opener, url, headers=None, data=None):
    request = urllib.request.Request(url, data=data, headers=headers or {})
    try:
        with opener.open(request, timeout=10) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='https://localhost:18443')
    parser.add_argument('--ca-cert', required=True)
    parser.add_argument('--admin-username', default='admin')
    parser.add_argument('--admin-password-file')
    args = parser.parse_args()
    password = (open(args.admin_password_file).read().strip() if args.admin_password_file
                else getpass.getpass('Administrator password: '))
    context = ssl.create_default_context(cafile=args.ca_cert)
    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),
                                         urllib.request.HTTPCookieProcessor(cookies))
    base = args.base_url.rstrip('/')
    assert fetch(opener, base + '/actuator/health')[1] == b'{"status":"UP"}'
    assert fetch(opener, base + '/monitor/metrics')[0] == 401
    assert fetch(opener, base + '/actuator/metrics')[0] == 401
    login_page = fetch(opener, base + '/login')[1].decode()
    token = re.search(r'name="_csrf" value="([^"]+)"', login_page)
    assert token, 'login CSRF token missing'
    form = urllib.parse.urlencode({'username': args.admin_username, 'password': password,
                                   '_csrf': token.group(1)}).encode()
    login_status, login_body, _ = fetch(opener, base + '/login',
                                       {'Content-Type': 'application/x-www-form-urlencoded'}, form)
    assert login_status == 200 and b'login?error' not in login_body, f'login status {login_status}: {login_body[:200]!r}'
    assert any(cookie.secure for cookie in cookies), 'session cookie is not Secure'
    assert fetch(opener, base + '/monitor/metrics')[0] == 200
    assert fetch(opener, base + '/test/hello')[0] == 404
    statuses = []
    for index in range(6):
        headers = {'X-Real-IP': f'198.51.100.{index+1}',
                   'X-Forwarded-For': f'203.0.113.{index+1}',
                   'Forwarded': f'for=192.0.2.{index+1}'}
        status, _, _ = fetch(opener, base + '/app/hello', headers)
        statuses.append(status)
    assert statuses == [200] * 5 + [429], statuses
    assert fetch(opener, base + '/app/fail')[0] == 500
    assert fetch(opener, base + '/app/echo', {'Content-Type': 'text/plain'}, b'm4-echo')[1] == b'm4-echo'
    assert fetch(opener, base + '/app/unlisted')[0] == 404
    assert fetch(opener, base + '/monitor/endpoints?window=5')[0] == 200
    assert fetch(opener, base + '/monitor/endpoints?window=15')[0] == 200
    assert fetch(opener, base + '/monitor/endpoints?window=8')[0] == 400
    assert fetch(opener, base + '/monitor/rate-limit')[0] == 200
    metrics = json.loads(fetch(opener, base + '/monitor/metrics')[1])
    assert metrics['blockedRequests'] >= 1 and metrics['serverErrors'] >= 1, metrics
    print(json.dumps({'health': 'UP', 'anonymous_management': 401,
                      'session_cookie_secure': True, 'production_test_route': 404,
                      'spoofed_header_hello_statuses': statuses,
                      'upstream_failure': 500, 'echo': 'm4-echo',
                      'unlisted_route': 404, 'p95_windows': [5, 15],
                      'accounting': {key: metrics[key] for key in
                                     ('attemptedRequests', 'allowedRequests', 'blockedRequests', 'serverErrors')}},
                     indent=2))


if __name__ == '__main__':
    main()
