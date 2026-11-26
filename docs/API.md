# HTTP API reference

Management endpoints require an administrator session. A browser request without a session redirects to `/login`; monitoring and Actuator API requests return 401. Login and logout use Spring Security form authentication and CSRF. `/auth/csrf` returns a token for the browser's logout form. Application traffic and health are public.

| Endpoint | Response and use |
| --- | --- |
| `GET /actuator/health` | `{"status":"UP"}` when healthy; no component detail |
| `GET /monitor/metrics` | Retained-period `attemptedRequests`, `allowedRequests`, `blockedRequests`, `unavailableRequests`, `successfulRequests`, `clientErrors`, `serverErrors`, `averageResponseTime`, `errorRate`; `totalRequests` aliases attempted |
| `GET /monitor/errors` | `errorRate`, `clientErrors`, `serverErrors`, `allowedRequests` |
| `GET /monitor/endpoints?window=5` | Endpoint stats for 5 or 15 minutes; unsupported values return 400; P95 is null for an empty sample |
| `GET /monitor/rpm` | Fifteen request counts by minute, last is current partial minute |
| `GET /monitor/blocked-trend` | Fifteen blocked counts by minute, last is current partial minute |
| `GET /monitor/rate-limit` | Configured default and exact-path policies for dashboard display |
| `GET /actuator/metrics` | Authenticated Spring metrics |
| `/app/<path>` | Public allowlisted upstream request; methods GET, POST, PUT, PATCH, DELETE |

For `/app/**`, HTTP 429 means the configured limit was reached; `Retry-After` is in seconds. HTTP 503 with `rate_limit_unavailable` means Redis could not enforce. Unlisted routes return 404; request body over the configured cap returns 413; unavailable upstream returns 502; timeout returns 504. Upstream response status and Content-Type are preserved for bounded responses. `X-RateLimit-Remaining` reports remaining allowance when Redis decides.

Application requests are grouped by exact path for limiting. Unknown paths are grouped as `/app/_other` for monitoring and limiting. Request bodies, query strings, credentials, and IP addresses are not exposed by the dashboard APIs.
