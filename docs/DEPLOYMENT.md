# Single-VM deployment

This guide targets a small Linux VM with Docker Engine and Compose v2. A practical starting point is 2 vCPU, 4 GiB RAM, and at least 20 GiB persistent disk, with monitoring of actual usage before sizing up. Building Maven images and running five containers need headroom beyond their configured memory limits. These are starting estimates, not measured capacity limits.

## Prerequisites and firewall

Create a DNS A record for `DOMAIN` pointing to the VM's public IPv4 address; add AAAA only if IPv6 is configured. Permit inbound TCP 22 from trusted administration addresses and TCP 80/443 from intended clients. Deny inbound 8080, 8081, 5432, and 6379. Caddy needs reachable 80/443 for automatic HTTPS. Keep the Docker daemon socket private and install security updates for the host and images.

## Configuration

Copy `deploy/env.prod.example` to a private `deploy/env.prod`, set mode 600, and replace every placeholder. Use distinct generated passwords for PostgreSQL and Redis and a BCrypt hash for the administrator (`htpasswd -nBC 12 admin`, keeping only the hash after `admin:` within single quotes). Do not commit the private file or print it in CI logs. Set `DOMAIN` to the DNS name. The Compose configuration fixes the upstream to private `demo:8081` and the trusted proxy to Caddy's `172.30.40.10` network address. If the subnet conflicts with an existing VM network, change the Caddy IP and `TRUSTED_PROXY_IP` together and retest spoof protection.

```sh
docker compose --env-file deploy/env.prod -f compose.prod.yaml config --quiet
docker compose --env-file deploy/env.prod -f compose.prod.yaml up -d --build --wait
docker compose --env-file deploy/env.prod -f compose.prod.yaml ps
curl --fail --show-error https://YOUR_DOMAIN/actuator/health
```

Only Caddy should have host port mappings. Confirm unauthenticated `/monitor/metrics` returns 401, `/test/hello` is unavailable, and allowed `/app/hello` traffic updates the dashboard after administrator login. Check `docker compose ... logs --tail=100` for startup errors without sharing logs that might contain environment data. Caddy stores its certificate state in `caddy_data`; PostgreSQL and Redis use named volumes.

## Local HTTPS verification

For a disposable local stack, use a private environment file with `DOMAIN=localhost`, `CADDYFILE_PATH=./deploy/Caddyfile.local`, `HTTP_PORT=18080`, and `HTTPS_PORT=18443`. Build/start with the same Compose commands. Caddy's local CA can be copied from its container and supplied to curl as `--cacert`; it should not be installed as a system trust root merely for this check. Use `https://localhost:18443`. The local CA is for verification only; the public Caddyfile uses automatic certificates for a real domain.

## Existing databases

A fresh empty PostgreSQL volume migrates through Flyway V1 and V2 automatically. For a nonempty M0/M1 database, back it up, inspect the table shape, then deliberately baseline V1 with Flyway CLI before starting this application. Do not auto-baseline an unknown schema. V2 adds the decision column if needed and creates indexes; Hibernate validates after migration. The automated migration tests cover fresh and reviewed legacy shapes.

## Backup and restore

Back up before updates. Store encrypted backups outside the VM and test restoration periodically. A logical backup command, run from the repository directory, is:

```sh
docker compose --env-file deploy/env.prod -f compose.prod.yaml exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > latensys.backup
```

For a restore, stop application traffic and restore into a **new empty database or isolated replacement volume** after checking the backup and schema version. Example for a deliberately created empty target database inside PostgreSQL:

```sh
cat latensys.backup | docker compose --env-file deploy/env.prod -f compose.prod.yaml exec -T postgres sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner'
```

The example `--clean` replaces database objects; perform it only against the intended restoration target with an independent backup. Redis rate-limit state and recent latency samples are transient and can be rebuilt; its volume persists restarts but is not a substitute for PostgreSQL backup.

## Update and rollback

Record the current Git revision and image IDs, take a PostgreSQL backup, review migrations, then rebuild and restart using `docker compose ... up -d --build --wait`. Verify health, login, `/app/hello`, 429 enforcement, and dashboard metrics. For rollback, restore the previous image or rebuild the previous Git revision in an isolated checkout, then start it with the same private environment. If a new migration is incompatible with the old binary, restore the matching PostgreSQL backup to an isolated replacement volume before switching traffic. Do not use `down -v` during routine updates.

## Secrets and rotation

Rotate the administrator password hash, database password, Redis password, and webhook URLs after compromise or historical exposure. Update the private environment and dependent services in a planned maintenance window; replacing a PostgreSQL environment variable alone does not change a password already initialized in a persistent database volume. Change that role's password inside PostgreSQL, update the environment, then restart clients. Keep historical Git credentials considered compromised even if removed from the current tree. This release does not rewrite history.
