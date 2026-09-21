# Oracle Cloud Infrastructure (OCI) hosting

Run the same **Model A** stack as [VPS_HOSTING.md](VPS_HOSTING.md): Docker Compose (`docker-compose.prod.yml`) + host TLS (Caddy sample in [`deploy/Caddyfile`](../deploy/Caddyfile)).

## Shape and CPU (important)

| Choice | Guidance |
| --- | --- |
| **VM.Standard.E2.x / E4** (AMD64) | **Recommended.** Backend images use `eclipse-temurin:17-*-alpine` (amd64). |
| **Ampere A1** (ARM64) | **Not supported** with current Dockerfiles. Use an AMD shape or change the backend base image first. |

Minimum: **4 GB RAM**, **50 GB** boot volume (20 GB is tight once images and Postgres data grow).

## 1. Create the compute instance

1. OCI Console → **Compute** → **Instances** → Create.
2. Image: **Ubuntu 22.04** or **24.04**.
3. Shape: **AMD64** (see table above), at least 2 OCPUs / 4 GB if available on your tenancy.
4. Networking: assign a **public IPv4** (or use a load balancer later).
5. SSH key: upload your public key.

## 2. Security list / NSG (firewall)

On the subnet or instance NSG, allow **ingress**:

| Port | Purpose |
| --- | --- |
| 22 | SSH |
| 80 | HTTP (Caddy ACME / redirect) |
| 443 | HTTPS |

Do **not** open **5432**, **8080**, or **4200** to the internet. The app listens on `127.0.0.1:4200` only; Caddy terminates TLS on 443.

Also configure **egress** for SMTP (587), package mirrors, and optional AI APIs.

## 3. DNS

Create an **A record** for your hostname → the instance public IP.

## 4. Install Docker on the VM

Same as [VPS_HOSTING.md §1](VPS_HOSTING.md#1-install-docker-ubuntu):

```bash
sudo usermod -aG docker "$USER"
# log out and back in
docker compose version
```

## 5. Deploy the application

```bash
sudo mkdir -p /opt
sudo git clone <YOUR_REPO_URL> /opt/finance-ai-automation-platform
sudo chown -R "$USER":"$USER" /opt/finance-ai-automation-platform
cd /opt/finance-ai-automation-platform
cp .env.example .env
```

Edit `.env` (never commit it):

| Variable | OCI notes |
| --- | --- |
| `POSTGRES_PASSWORD` / `SPRING_DATASOURCE_PASSWORD` | Strong unique values |
| `APP_JWT_SECRET` | `openssl rand -base64 48` |
| `APP_FRONTEND_BASE_URL` | `https://your.domain.com` |
| `APP_STORAGE_PROVIDER` | `local` for single-VM MVP (`finance_uploads` volume); or `s3` with [Object Storage](https://docs.oracle.com/en-us/iaas/Content/Object/Concepts/objectstorageoverview.htm) S3-compatible API |
| `APP_EMAIL_PROVIDER` | `smtp` for production (verification emails) |
| `APP_CORS_ALLOWED_ORIGINS` | Leave empty for Model A |

Start production Compose:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up --build -d
docker compose -f docker-compose.prod.yml ps
curl -sS http://127.0.0.1:4200/api/v1/health/ready
```

Flyway should reach **V35** on a fresh database. Do not point this release at a database used by an older (V29) checkout.

## 6. TLS with Caddy

```bash
sudo apt install -y caddy
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
# edit hostname
sudo systemctl enable --now caddy
sudo systemctl reload caddy
```

## 7. Object storage (optional)

For durable documents off the VM disk:

1. Create a bucket and customer secret keys in OCI Object Storage.
2. Set `APP_STORAGE_PROVIDER=s3`, bucket, region, and S3-compatible endpoint/credentials per [DEPLOYMENT.md](DEPLOYMENT.md).

## 8. Backups

Enable [`deploy/backup/`](../deploy/backup/) on the VM and sync dumps off-instance (Object Storage, another region, etc.). See [VPS_HOSTING.md](VPS_HOSTING.md#backup-after-first-boot).

## 9. Redis (only if you scale out)

Single VM: leave `APP_AUTH_RATE_LIMIT_STORE=memory` (default). If you run **multiple** backend instances behind a load balancer, set `APP_AUTH_RATE_LIMIT_STORE=redis`, deploy Redis privately, and set `APP_AUTH_REDIS_HOST` / `APP_AUTH_REDIS_PORT`.

## Related docs

- [CLOUD_HOSTING_READINESS.md](CLOUD_HOSTING_READINESS.md) — audit checklist and P0/P1 items
- [FIRST_PAID_PILOT_DEPLOYMENT_CHECKLIST.md](FIRST_PAID_PILOT_DEPLOYMENT_CHECKLIST.md) — go-live checklist
