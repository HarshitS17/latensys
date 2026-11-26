# Recruiter demonstration

Use private synthetic administrator credentials. Start the local production Compose stack as described in [deployment guide](DEPLOYMENT.md#local-https-verification), or use the README local development setup. Never put the password in screenshots.

1. Open the HTTPS URL and sign in. The dashboard should load without a horizontal scrollbar at desktop and narrow widths.
2. Send one `GET /app/hello` and one `GET /app/fail`. The former reaches the independent demo; the latter keeps the demo's HTTP 500. Use `POST /app/echo` with a short text body to show body preservation.
3. Send six `GET /app/hello` requests within 60 seconds from one client address. The sixth is HTTP 429 and the demo's `/stats` call count does not increase for it.
4. Reload or wait for the five-second poll. Show attempts, blocked requests, error rate, recent endpoint latency, and the current partial minute. Switch endpoint window from 5 to 15 minutes; the empty window may show no P95 until samples exist.
5. Open the rate-limit panel: `/app/hello` shows the exact 5/60 override. Change `RATE_LIMIT_APP_HELLO_MAX_REQUESTS` only in a private environment file, restart Latensys, and confirm the panel and enforcement agree. Restore 5 afterward.
6. Capture the live dashboard after synthetic traffic at desktop and narrow widths. The screenshot should show no credentials, cookies, or private environment files. Label the machine, date, traffic, and viewport. Do not substitute a mockup for a live capture.

The demo supports `/hello`, `/fail`, `/echo`, `/stats`, and `/slow`. The proxy's exact allowlist can be changed at startup. The demonstration is local unless a public VM deployment is actually completed.
