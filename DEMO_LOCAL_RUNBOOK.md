# Demo local runbook (tomorrow)

**Goal:** Stable Harbor Ledger / Cedar Café walkthrough. **Do not** use from-scratch registration on demo day unless you have 35+ minutes and rehearsed Step 8B (owner client-access API).

---

## Recommended stack (tonight / morning)

1. **Start Docker Desktop** (required for Postgres + optional full stack).
2. **Postgres + backend + frontend (Compose)** — simplest when `npm` / local Gradle hit memory limits:

```powershell
cd C:\Users\HP\Downloads\finance-ai-automation-platform

# Copy secrets once (never commit .env):
# copy .env.example .env
# Set APP_JWT_SECRET to a long random string (32+ chars).

docker compose up -d postgres
docker compose build backend    # IMPORTANT: image must match current code (see P0 below)
docker compose up -d backend frontend
```

3. **Wait for health**

```powershell
# Backend
curl http://localhost:8080/api/v1/health/ready

# UI
start http://localhost:4200
```

4. **Seed demo data** (idempotent; safe to re-run):

```powershell
python demo/seed_demo.py
python demo/verify_demo_state.py
```

**Expected after seed:** client **Cedar Café (Pvt) Ltd**, **1** document in inbox (Keells), **open** document request “September rent invoice”, **0** September bank lines (import during demo), **8** portfolio clients, August history for Trends.

---

## Alternative: local backend + Compose Postgres

If you need **latest code** without rebuilding the Docker backend JAR:

```powershell
docker compose up -d postgres

cd backend
$env:SPRING_PROFILES_ACTIVE="local"
# Load SPRING_DATASOURCE_* and APP_JWT_SECRET from your .env
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:4200"
$env:APP_AUTH_COOKIE_SECURE="false"
$env:APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL="priya@harborledger.demo"
$env:APP_AI_ENABLED="false"
$env:APP_EMAIL_PROVIDER="log"
.\gradlew.bat :platform-app:bootRun

cd ..\frontend
npm start
```

If Gradle fails with **OutOfMemoryError**, close other apps or use the Docker backend after `docker compose build backend`.

---

## Demo credentials (local only — not production)

| Role | Email | Password |
|------|-------|----------|
| Firm admin | `priya@harborledger.demo` | `DemoPass123!` |
| Accountant | `nimal@harborledger.demo` | `DemoPass123!` |
| Business owner (Cedar) | `amaya@cedarcafe.lk` | `DemoPass123!` |

**Firm:** Harbor Ledger Partners · **Primary client:** Cedar Café (Pvt) Ltd · **Currency:** LKR

**Platform admin (optional):** same Priya account can open `/platform` after bootstrap email is set (see `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL`).

**JWT / DB:** keep `APP_JWT_SECRET` and `POSTGRES_PASSWORD` in `.env` only — never paste on slides or commit.

---

## Sample files (repo)

| File | Use |
|------|-----|
| `demo/files/keells-receipt.pdf` | Pre-seeded inbox; backup if upload fails |
| `demo/files/bank-sept.csv` | Banking → Import (4 lines: Keells, card sale, CEB, bank fee) |
| `demo/files/utility-bill.pdf` | Owner upload for rent request |

---

## Reset (clean September books)

```powershell
docker compose down -v
docker compose up -d postgres backend frontend
# wait for ready, then:
python demo/seed_demo.py
```

---

## Pre-demo smoke (2 minutes)

```powershell
python demo/verify_demo_state.py
```

Login at http://localhost:4200/login as Priya → Dashboard loads → header **Active client** = Cedar Café.

---

## Known environment issues (verified tonight)

| Issue | Mitigation |
|-------|------------|
| **Stale Docker backend image** | `GET /api/v1/work/month-end-command-center` returns **404** on an old image; run `docker compose build backend` or local `bootRun`. Use **Close** (`/app/close`) and **My work** (`/app/work`) as fallback. |
| Local `npm start` / Gradle **OOM** on low RAM | Use `docker compose up -d frontend` (port 4200 → nginx). |
| Docker Desktop stopped | Postgres on 5432 will be down; start Desktop first. |
