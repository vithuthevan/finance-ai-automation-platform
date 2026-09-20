# VPS hosting with Docker Compose

Run the full stack (PostgreSQL + Spring Boot + Angular) on a single VPS using the repo’s [`docker-compose.yml`](../docker-compose.yml). This is **Model A** (same origin): the frontend Nginx container proxies `/api` to the backend.

```
Browser → HTTPS (Caddy / Cloudflare) → frontend:4200 → /api → backend:8080 → postgres
```

For architecture and Model B (split hosts), see [DEPLOYMENT.md](DEPLOYMENT.md).

## Prerequisites

| Requirement | Notes |
| --- | --- |
| VPS | Ubuntu 22.04/24.04 recommended |
| RAM | **2 GB minimum**; **4 GB preferred** (Compose builds the backend JAR on the server) |
| Disk | 20+ GB |
| Docker | Docker Engine + Compose plugin |
| Domain | A record → VPS IP (optional for first smoke test; required for HTTPS) |

Open ports **22**, **80**, and **443**. Keep **4200** / **8080** open only while debugging; never leave **5432** public.

## 1. Install Docker (Ubuntu)

Follow [Docker’s Ubuntu install guide](https://docs.docker.com/engine/install/ubuntu/), then:

```bash
sudo usermod -aG docker "$USER"
# log out and back in so the group applies
docker compose version
```

## 2. Clone the repository

```bash
sudo mkdir -p /opt
sudo git clone <YOUR_REPO_URL> /opt/finance-ai-automation-platform
sudo chown -R "$USER":"$USER" /opt/finance-ai-automation-platform
cd /opt/finance-ai-automation-platform
```

## 3. Configure secrets

```bash
cp .env.example .env
```

Edit `.env` on the server. **Never commit `.env`.**

| Variable | Action |
| --- | --- |
| `POSTGRES_PASSWORD` | Strong unique password |
| `SPRING_DATASOURCE_PASSWORD` | Same value as `POSTGRES_PASSWORD` (local tools); Compose also injects it into the backend |
| `APP_JWT_SECRET` | Required in `prod`. Generate ≥32 random bytes, base64-encoded (see below) |
| `APP_CORS_ALLOWED_ORIGINS` | Leave **empty** for same-origin Compose |
| `APP_STORAGE_PROVIDER` | `local` for MVP (Compose volume); use `s3` for real production |
| `APP_AI_ENABLED` | `false` until you have an API key |
| `APP_FRONTEND_BASE_URL` | Set to `https://your.domain.com` after TLS is live |

Generate a JWT secret:

```bash
openssl rand -base64 48
```

Paste the output into `APP_JWT_SECRET`.

Compose already sets `SPRING_PROFILES_ACTIVE=prod` and points JDBC at the `postgres` service (`jdbc:postgresql://postgres:5432/...`).

## 4. Start the stack

```bash
cd /opt/finance-ai-automation-platform
docker compose up --build -d
```

First build can take several minutes. Check status:

```bash
docker compose ps
docker compose logs -f backend
```

### Temporary URLs (before HTTPS)

| Check | URL |
| --- | --- |
| UI | `http://SERVER_IP:4200` |
| API health | `http://SERVER_IP:8080/api/v1/health` |
| API ready | `http://SERVER_IP:8080/api/v1/health/ready` |

There are no seeded login users. Register a firm via the UI, then sign in. Flyway seeds roles and subscription plans only.

## 5. HTTPS with Caddy

Install [Caddy](https://caddyserver.com/docs/install) on the host. Use the sample config:

- Template: [`deploy/Caddyfile`](../deploy/Caddyfile)
- Replace `your.domain.com` with your real hostname
- DNS A record must already point at the VPS

```bash
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
# edit: replace your.domain.com
sudo systemctl enable --now caddy
sudo systemctl reload caddy
```

Caddy obtains and renews TLS automatically and reverse-proxies to `localhost:4200`. Same-origin stays intact: the browser uses `https://your.domain.com/api/...`, and the frontend container still proxies to `backend:8080`.

**After TLS works**, close public access to app ports (see [Firewall](#7-firewall)).

Alternatives: Cloudflare orange-cloud proxy to the VPS, or host Nginx terminating TLS in front of port 4200.

## 6. Hardening after first successful boot

- Firewall: only **22 / 80 / 443** public ([section 7](#7-firewall))
- Volumes already persist data: `postgres_data`, `finance_uploads`
- **Backups (required after go-live):** see [Backup after first boot](#backup-after-first-boot)
- Documents in production: set `APP_STORAGE_PROVIDER=s3` and the `APP_STORAGE_S3_*` variables ([DEPLOYMENT.md](DEPLOYMENT.md)); enable bucket versioning
- Set `APP_FRONTEND_BASE_URL=https://your.domain.com` if you enable email links
- Disaster recovery: [DISASTER_RECOVERY_RUNBOOK.md](reports/DISASTER_RECOVERY_RUNBOOK.md)

## 7. Firewall

Using `ufw` (adjust if you use a cloud security group instead):

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
# during first debug only:
# sudo ufw allow 4200/tcp
# sudo ufw allow 8080/tcp
sudo ufw enable
sudo ufw status
```

Do **not** expose **5432**, **8080**, or **4200** once Caddy (or another reverse proxy) is in place.

## 8. Updates / redeploy

Run a backup **before** pulling a release that includes new Flyway migrations:

```bash
sudo /opt/finance-ai-automation-platform/deploy/backup/run-backup.sh
cd /opt/finance-ai-automation-platform
git pull
docker compose -f docker-compose.prod.yml --env-file .env up --build -d
```

Flyway applies pending migrations automatically when the backend starts. There are no down migrations — see [BACKUP_RECOVERY_IMPLEMENTATION.md](reports/BACKUP_RECOVERY_IMPLEMENTATION.md) if a migration fails.

## Backup after first boot

On-server volumes are **not** a disaster-recovery plan. After the first production boot:

1. Copy `deploy/backup/backup.env.example` to `deploy/backup/backup.env` and set `BACKUP_ROOT`, retention, and off-server credentials.
2. `sudo mkdir -p /var/backups/finance-platform /var/log/finance-platform`
3. Run `sudo deploy/backup/run-backup.sh` once and confirm a timestamped `.sql.gz` (not `.tmp`) exists.
4. Enable `BACKUP_OFFSITE_ENABLED=true` and sync to a **separate** S3-compatible bucket (different account from application storage).
5. Install `deploy/backup/systemd/finance-platform-backup.timer` or `deploy/backup/cron.example` (daily 02:00).
6. Archive `.env` / `APP_JWT_SECRET` in a password manager.
7. After a staging restore drill, sign the checklist in [DISASTER_RECOVERY_RUNBOOK.md](reports/DISASTER_RECOVERY_RUNBOOK.md). Until then recovery is **NOT YET VALIDATED**.

Full procedure: [BACKUP_RECOVERY_IMPLEMENTATION.md](reports/BACKUP_RECOVERY_IMPLEMENTATION.md).

## Post-deploy smoke checklist

Run these after `docker compose up` and again after enabling HTTPS.

### Containers and logs

- [ ] `docker compose ps` — `postgres`, `backend`, and `frontend` are up
- [ ] `docker compose logs backend` — no fatal datasource / Flyway / JWT errors
- [ ] Postgres healthy: Compose healthcheck passed (`pg_isready`)

### Health endpoints

Before TLS (direct ports):

```bash
curl -sS "http://127.0.0.1:8080/api/v1/health"
curl -sS "http://127.0.0.1:8080/api/v1/health/ready"
```

After Caddy / public HTTPS:

```bash
curl -sS "https://your.domain.com/api/v1/health"
curl -sS "https://your.domain.com/api/v1/health/ready"
```

- [ ] Both return success (ready should confirm DB connectivity)

### UI and registration

- [ ] Open `https://your.domain.com` (or `http://SERVER_IP:4200` during debug) — Angular shell loads
- [ ] Register a new firm / user
- [ ] Sign in (registration does **not** return a JWT; login is required)
- [ ] A protected page loads (e.g. dashboard / clients) without CORS errors in the browser console

### Same-origin API via Nginx

```bash
curl -sS "https://your.domain.com/api/v1/health"
# or during debug:
curl -sS "http://127.0.0.1:4200/api/v1/health"
```

- [ ] Request succeeds through the frontend proxy (not only via `:8080`)

### Ports closed

- [ ] From an external network (or a second machine), **5432**, **8080**, and **4200** do not accept connections
- [ ] **80** / **443** (and **22** for SSH) are reachable as intended
- [ ] Cloud security group matches the host firewall

### Optional

- [ ] Upload a small document (local storage volume) if you use document features
- [ ] `APP_FRONTEND_BASE_URL` matches the public HTTPS origin

## When to move off a single VPS

- Managed Postgres (Neon / RDS / etc.): set `SPRING_DATASOURCE_*` and remove or stop the Compose `postgres` service
- Zero VM maintenance: split hosts per Model B in [DEPLOYMENT.md](DEPLOYMENT.md) and [deploy/README.md](../deploy/README.md)
