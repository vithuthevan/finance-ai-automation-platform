# Deployment

The repository is a monorepo with **one backend deployable** and **one frontend deployable**. They are not packaged into a single Spring JAR.

```
Users
  │
  ▼
HTTPS / CDN
  │
  ├──────── Angular frontend
  │
  └──────── /api → Spring Boot (platform-app)
                 │
                 ├── PostgreSQL
                 ├── Object storage (production)
                 └── Optional AI / email providers
```

## Model A — same domain

Nginx (or an equivalent reverse proxy) serves Angular at `/` and proxies `/api/` to Spring Boot.

- Frontend `assets/config.json`: `{ "apiBaseUrl": "/api/v1" }`
- `APP_CORS_ALLOWED_ORIGINS` can be empty
- Config: `deploy/nginx/same-origin.conf`

Works with Docker Compose, a VM + Nginx, or any host that can run both containers behind one hostname.

**VPS runbook (recommended for a single server):** [VPS_HOSTING.md](VPS_HOSTING.md) — Docker Compose, secrets, Caddy HTTPS, firewall, redeploy, and a post-deploy smoke checklist. Sample TLS config: [`deploy/Caddyfile`](../deploy/Caddyfile).

## Model B — separate hosts

- Frontend: S3 + CloudFront, Cloudflare Pages, Netlify, Vercel, or an Nginx container using `frontend-only.conf`
- Backend: ECS, EC2, Azure App Service, Cloud Run, Railway, Render, or any Docker host
- Database: RDS, Azure Database for PostgreSQL, Cloud SQL, Supabase, Neon, or other managed PostgreSQL
- Documents: S3-compatible object storage
- Set `assets/config.json` to `https://api.example.com/api/v1`
- Set `APP_CORS_ALLOWED_ORIGINS` to the exact frontend origin
- Do not use `*` CORS with JWT Bearer credentials

## Configuration

Copy `.env.example` to `.env`. Production must set `SPRING_PROFILES_ACTIVE=prod` and `APP_JWT_SECRET`. There is no production fallback JWT secret.

The backend honors `X-Forwarded-Proto` / `X-Forwarded-Host` via `server.forward-headers-strategy=framework`.

## Health

Use `GET /api/v1/health` and `GET /api/v1/health/ready` for load-balancer checks. Do not expose management/actuator internals.

## Auth note

V1 stores JWT access/refresh tokens in the browser via `AuthService` and attaches them with `authInterceptor`. A future httpOnly cookie session would be a controlled auth change, not part of this restructuring.

## Persistence

- Database files must live on a volume or managed PostgreSQL, not in the application image.
- Local document storage in Compose is mounted at `/data/uploads` (`finance_uploads` volume).
- Production should set `APP_STORAGE_PROVIDER=s3`.
- Backup, restore, and disaster recovery: [BACKUP_RECOVERY_IMPLEMENTATION.md](reports/BACKUP_RECOVERY_IMPLEMENTATION.md), [DISASTER_RECOVERY_RUNBOOK.md](reports/DISASTER_RECOVERY_RUNBOOK.md), scripts in [`deploy/backup/`](../deploy/backup/).
