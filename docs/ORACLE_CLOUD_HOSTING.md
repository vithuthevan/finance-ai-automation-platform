# Oracle Cloud Infrastructure (OCI) — full hosting walkthrough

End-to-end: **sign in to Oracle Cloud** → create a VM → deploy this repo with Docker Compose → HTTPS with Caddy.

Architecture matches [VPS_HOSTING.md](VPS_HOSTING.md) (**Model A**): browser → Caddy (:443) → frontend on `localhost:4200` → `/api` → backend → Postgres inside Compose.

**CPU warning:** use an **AMD64** VM shape only. **Ampere A1 (ARM)** does not run the current backend Docker image. See [shape choice](#6-choose-instance-shape-amd64-only).

---

## What you need before you start

| Item | Notes |
| --- | --- |
| Oracle account | Email + card for verification (Always Free tier still asks for card; you are not charged for eligible free resources if you stay within limits) |
| Domain name (recommended) | For automatic HTTPS via Caddy (e.g. `app.yourfirm.com`) |
| SSH client | Windows 10/11: **OpenSSH** (`ssh` in PowerShell). Optional: PuTTY |
| Git repo URL | `https://github.com/vithuthevan/finance-ai-automation-platform.git` |

---

## 1. Sign up or sign in to Oracle Cloud

1. Open **[https://cloud.oracle.com](https://cloud.oracle.com)**.
2. Click **Sign in** (existing account) or **Start for free** / **Sign up**.
3. Complete email verification and identity steps Oracle requests.
4. After login you land in the **OCI Console** (web UI). The top bar shows your **region** (e.g. `UK London`, `AP Mumbai`). Pick a region close to your users and keep everything in **one** region.

### 1.1 First-time console setup (if prompted)

Oracle may ask you to:

- **Create a password** for your tenancy admin user.
- **Choose a home region** — cannot be changed later for some free-tier resources; choose carefully.
- **Name your tenancy** — internal label only.

You do not need every OCI service enabled; this guide uses **Compute** and default **Networking**.

---

## 2. Understand compartments (one minute)

Resources live in **compartments** (like folders). Default: compartment named after your tenancy, e.g. `vithu (root)`.

- Top-left **≡ menu** → **Identity & Security** → **Compartments** — optional; root is fine for a single app VM.
- Ensure the **compartment** selector at the top of the console matches where you will create the instance (usually root).

---

## 3. Create an SSH key on your PC (Windows)

On your Windows machine, open **PowerShell**:

```powershell
ssh-keygen -t ed25519 -C "oci-finance-platform" -f $env:USERPROFILE\.ssh\oci_finance_platform
```

Press Enter for no passphrase (or set one if you prefer).

- **Private key:** `C:\Users\<You>\.ssh\oci_finance_platform` — keep secret, never upload to GitHub.
- **Public key:** `C:\Users\<You>\.ssh\oci_finance_platform.pub` — you will paste this into OCI when creating the VM.

Show the public key to copy:

```powershell
Get-Content $env:USERPROFILE\.ssh\oci_finance_platform.pub
```

---

## 4. Create a virtual cloud network (VCN) — usually automatic

When you create a compute instance, OCI can **create a VCN for you** (simplest path).

Advanced users can use **≡ menu** → **Networking** → **Virtual cloud networks** → **Start VCN Wizard** → **VCN with Internet Connectivity**.

For this app you need:

- A **public subnet** with a route to an **Internet Gateway**
- The instance gets a **public IPv4** address

---

## 5. Create the compute instance

1. **≡ menu** → **Compute** → **Instances**.
2. **Create instance**.

### 5.1 Name and compartment

- **Name:** e.g. `finance-platform-prod`
- **Compartment:** root (or your chosen compartment)

### 5.2 Placement

- **Availability domain:** any available AD in your region.

### 5.3 Image and shape

- **Image:** **Ubuntu 22.04** or **24.04** (Canonical Ubuntu).
- **Shape:** click **Change shape**:
  - **Shape series:** **Intel** or **AMD** (VM.Standard.E2, E3, E4, etc.) — **not** Ampere.
  - For Always Free, look for **VM.Standard.E2.1.Micro** (1 GB RAM) — **too small** for building Docker images; prefer **4 GB RAM** minimum (paid small shape or trial credits).
  - Practical minimum: **2 OCPU / 4 GB RAM** if your tenancy allows it.

### 5.4 Networking

- **Primary VCN:** create new or pick existing.
- **Subnet:** **public subnet**.
- **Assign a public IPv4 address:** **Yes**.

### 5.5 SSH keys

- Choose **Upload public key files** or **Paste public key**.
- Paste contents of `oci_finance_platform.pub`.

### 5.6 Boot volume

- **Size:** **50 GB** recommended (default 47 GB is OK).

### 5.7 Create

Click **Create**. Wait until **State** = **Running**. Note the **Public IP address** (e.g. `132.145.x.x`).

Default login user on Ubuntu images is **`ubuntu`** (not `opc`).

---

## 6. Choose instance shape (AMD64 only)

| Shape | Use |
| --- | --- |
| **VM.Standard.E2 / E3 / E4** (x86) | **Yes** — matches `eclipse-temurin:17-*-alpine` in `backend/Dockerfile` |
| **VM.Standard.A1** (Ampere ARM) | **No** — image build/run will fail unless Dockerfiles change |

---

## 7. Open firewall ports in OCI (security list)

The VM’s subnet has a **security list**. Ingress must allow web and SSH.

1. **≡ menu** → **Networking** → **Virtual cloud networks** → your VCN.
2. Click the **public subnet** → **Security list** link.
3. **Add ingress rules** (if missing):

| Source CIDR | Protocol | Dest port | Description |
| --- | --- | --- | --- |
| `0.0.0.0/0` | TCP | 22 | SSH |
| `0.0.0.0/0` | TCP | 80 | HTTP (Caddy / ACME) |
| `0.0.0.0/0` | TCP | 443 | HTTPS |

Do **not** add rules for **5432**, **8080**, or **4200** publicly. Production Compose binds the UI to **127.0.0.1:4200** only.

**Egress:** default “allow all outbound” is fine (SMTP 587, Docker pulls, apt).

---

## 8. SSH into the VM from Windows

Replace `PUBLIC_IP` with the instance public IP:

```powershell
ssh -i $env:USERPROFILE\.ssh\oci_finance_platform ubuntu@PUBLIC_IP
```

First connection: type `yes` to trust the host key.

If connection times out: check instance is **Running**, security list has port **22**, and your local network allows outbound SSH.

---

## 9. Install Docker on Ubuntu (on the VM)

Run on the VM (after SSH):

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

Log out and SSH in again so group `docker` applies:

```bash
exit
```

```powershell
ssh -i $env:USERPROFILE\.ssh\oci_finance_platform ubuntu@PUBLIC_IP
```

Verify:

```bash
docker compose version
```

---

## 10. Clone the application and configure `.env`

On the VM:

```bash
sudo mkdir -p /opt
sudo git clone https://github.com/vithuthevan/finance-ai-automation-platform.git /opt/finance-ai-automation-platform
sudo chown -R "$USER":"$USER" /opt/finance-ai-automation-platform
cd /opt/finance-ai-automation-platform
cp .env.example .env
nano .env
```

Set at minimum (generate secrets on the VM):

```bash
openssl rand -base64 48
```

| Variable | What to set |
| --- | --- |
| `POSTGRES_USER` | e.g. `finance` |
| `POSTGRES_PASSWORD` | strong random |
| `SPRING_DATASOURCE_USERNAME` | same as `POSTGRES_USER` |
| `SPRING_DATASOURCE_PASSWORD` | same as `POSTGRES_PASSWORD` |
| `APP_JWT_SECRET` | output of `openssl rand -base64 48` |
| `APP_EMAIL_PROVIDER` | `smtp` for real mail, or `log` only for a private lab (prod profile may restrict `log`) |
| `MAIL_*` | your SMTP provider if using `smtp` |
| `APP_FRONTEND_BASE_URL` | `https://your.domain.com` once DNS exists |
| `APP_STORAGE_PROVIDER` | `local` for single-VM MVP |
| `APP_CORS_ALLOWED_ORIGINS` | leave **empty** for same-origin |

Never commit `.env`.

---

## 11. Start production Docker Compose

```bash
cd /opt/finance-ai-automation-platform
docker compose -f docker-compose.prod.yml --env-file .env up --build -d
```

First build may take **15–30+ minutes** on a small VM.

Check:

```bash
docker compose -f docker-compose.prod.yml ps
curl -sS http://127.0.0.1:4200/api/v1/health/ready
```

On a **fresh** database, Flyway should migrate to **V35**. Do not attach an old database from a pre-V30 release.

### 11.1 Access UI before you have HTTPS (Windows SSH tunnel)

Because the UI is on `127.0.0.1:4200` on the server, open a tunnel from your PC:

```powershell
ssh -i $env:USERPROFILE\.ssh\oci_finance_platform -L 4200:127.0.0.1:4200 ubuntu@PUBLIC_IP
```

Leave that window open. In the browser: **http://localhost:4200** — register a firm and sign in (no demo users in prod).

---

## 12. DNS (for real HTTPS)

At your DNS provider (Cloudflare, Route53, registrar, etc.):

| Type | Name | Value |
| --- | --- | --- |
| A | `app` (or `@`) | VM **public IP** |

Wait for propagation, then set in `.env`:

```text
APP_FRONTEND_BASE_URL=https://app.yourdomain.com
```

Restart backend if you changed `.env`:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

---

## 13. Install Caddy and enable HTTPS on the VM

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
sudo cp /opt/finance-ai-automation-platform/deploy/Caddyfile /etc/caddy/Caddyfile
sudo nano /etc/caddy/Caddyfile
```

Replace `your.domain.com` with your real hostname (must match DNS).

```bash
sudo systemctl enable --now caddy
sudo systemctl reload caddy
```

Browse **https://app.yourdomain.com**. API calls use **https://app.yourdomain.com/api/...** (same origin).

---

## 14. Host firewall on the VM (recommended)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Do not expose **4200** or **8080** publicly after Caddy works.

---

## 15. After go-live

- **Backups:** [VPS_HOSTING.md — Backup after first boot](VPS_HOSTING.md#backup-after-first-boot) and [`deploy/backup/`](../deploy/backup/).
- **Checklist:** [FIRST_PAID_PILOT_DEPLOYMENT_CHECKLIST.md](FIRST_PAID_PILOT_DEPLOYMENT_CHECKLIST.md).
- **Readiness audit:** [CLOUD_HOSTING_READINESS.md](CLOUD_HOSTING_READINESS.md).
- **Updates:**

```bash
cd /opt/finance-ai-automation-platform
git pull
docker compose -f docker-compose.prod.yml --env-file .env up --build -d
```

---

## Optional: OCI Object Storage for documents

Single VM MVP can use the `finance_uploads` Docker volume (`APP_STORAGE_PROVIDER=local`). For production durability, use OCI Object Storage with S3-compatible API keys and set `APP_STORAGE_PROVIDER=s3` per [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Optional: Redis (multiple app servers only)

One VM: keep `APP_AUTH_RATE_LIMIT_STORE=memory`. Multiple backend replicas behind a load balancer: set `redis` and `APP_AUTH_REDIS_HOST` / `APP_AUTH_REDIS_PORT` (Redis not included in Compose by default).

---

## Related docs

- [VPS_HOSTING.md](VPS_HOSTING.md) — same Compose/Caddy model, provider-neutral
- [DEPLOYMENT.md](DEPLOYMENT.md) — Model A vs Model B, CORS, storage
