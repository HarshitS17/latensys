# v1.0 release checklist

This file is a review gate, not a claim that a public release already happened.

## Code and verification

- [x] Java 17 `clean verify` passes: 38 tests, zero failures/errors/skips.
- [x] Demo application package succeeds.
- [x] Both production images build on ARM64 with non-root users and health checks.
- [x] Production Compose config validates; only Caddy publishes ports.
- [x] Local HTTPS stack is healthy; administrator login and Secure session cookie work.
- [x] Real upstream success, HTTP 500, HTTP 429, policy display, dashboard updates, and P95 selection are checked.
- [x] Spoofed forwarding headers cannot reset the rate limit.
- [x] Fresh Flyway V1/V2 migrations and production test-route absence are verified.
- [ ] Export actual synthetic dashboard screenshots as repository artifacts; browser policy blocked export. Desktop was captured inline; narrow layout was verified in M3 before M4, with no M4 CSS changes.
- [x] JavaScript/Python syntax, `git diff --check`, and tracked-secret scan pass.

## Release owner review

- [ ] Review the M4 report, changelog, screenshots, and release notes.
- [ ] Rotate any credentials historically exposed in Git; keep private environment files out of Git.
- [ ] Provide/authorize VM and domain if public deployment is desired; then verify public HTTPS and firewall rules.
- [ ] Decide on and create the commit, tag, GitHub release, and public deployment manually after review.
