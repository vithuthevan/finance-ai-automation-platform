# Deployment

Provider-neutral. The repository is not wired to a single cloud.

## Model A — same domain

```
https://finance.example.com/        → Angular (this nginx)
https://finance.example.com/api/*   → Spring Boot
```

Use `nginx/same-origin.conf`. Frontend `assets/config.json` stays:

```json
{ "apiBaseUrl": "/api/v1" }
```

CORS can be empty because the browser origin matches the API.

## Model B — separate hosts

```
https://app.example.com   → Angular
https://api.example.com   → Spring Boot
```

Use `nginx/frontend-only.conf` (or S3/CloudFront/Pages). Set:

```json
{ "apiBaseUrl": "https://api.example.com/api/v1" }
```

and `APP_CORS_ALLOWED_ORIGINS=https://app.example.com`.

Do not use `*` with credentialed JWT requests.

## TLS

Terminate HTTPS at Nginx, AWS ALB, Cloudflare, or the host platform. The backend uses `server.forward-headers-strategy=framework` so `X-Forwarded-Proto` is honored.

### Caddy on a VPS (Model A)

Sample config: [`Caddyfile`](Caddyfile). It reverse-proxies `https://your.domain.com` to the Compose frontend on `localhost:4200`. Full steps (secrets, `docker compose`, firewall, smoke checks): [docs/VPS_HOSTING.md](../docs/VPS_HOSTING.md).

Backup and disaster recovery (PostgreSQL + documents): [`backup/`](backup/) and [BACKUP_RECOVERY_IMPLEMENTATION.md](../BACKUP_RECOVERY_IMPLEMENTATION.md).
