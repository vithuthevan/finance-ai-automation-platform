# From-scratch demo — reset guide

Use this **immediately before** a live from-scratch presentation. Goal: **no demo firms, clients, users (except Flyway bootstrap), transactions, documents, or bank imports** — so registration through the UI is the first business action.

## What must exist (infrastructure only)

| Layer | Required | Created during demo? |
| --- | --- | --- |
| PostgreSQL 16 | Yes | No |
| Flyway migrations applied | Yes (roles, `subscription_plans`, schema) | No |
| `APP_JWT_SECRET` set (local/docker) | Yes | No |
| Backend `:platform-app:bootRun` or Docker backend | Yes | No |
| Frontend `npm start` (proxy to API) | Yes | No |
| Local storage path for uploads | Yes (empty or disposable) | Files added during demo |
| `demo/files/*` on disk | Yes (Keells PDF, utility bill, `bank-sept.csv`) | No |

**Do not run** `demo/seed_demo.py` for this demo. That script creates Harbor Ledger / Cedar Café seeded data.

**Do not** point the presentation at a database you use for the seeded Harbor Ledger walkthrough unless you reset it first.

## Recommended: isolated disposable database

Best practice: a **separate database name** from your seeded demo.

1. Create an empty database, for example `finance_platform_fromscratch`.
2. Point the backend at it (PowerShell example):

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/finance_platform_fromscratch"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "postgres"
$env:APP_JWT_SECRET = "<same strong secret you use locally>"
cd backend
./gradlew :platform-app:bootRun
```

3. Start frontend in another terminal:

```powershell
cd frontend
npm start
```

4. On first boot, Flyway creates schema + seeds **platform reference data only** (roles, subscription plans). **No firms.**

## Alternative: wipe one database (destructive)

Only if this database is **not** your preserved seeded demo.

### Docker Compose (removes Postgres volume)

```powershell
docker compose down -v
docker compose up --build
```

This deletes **all** tenants in that stack’s Postgres volume.

### SQL reset (single database)

Connect to the target database and run:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
```

Restart the backend so Flyway migrates from scratch.

### Optional: clear local upload files

If using local storage:

```powershell
Remove-Item -Recurse -Force .\data\storage\* -ErrorAction SilentlyContinue
```

(or the path in `APP_STORAGE_LOCAL_ROOT` / `STORAGE_LOCAL_PATH`)

## Verify empty state (30 seconds)

1. Open `http://localhost:4200/login`
2. **Register a firm** must **not** show “firm name already exists” unless you intentionally keep prior data.
3. After a test registration + login, **Clients** should be empty until you create one.

To undo a rehearsal registration without full DB wipe: register with **unique** firm name + email each run, or wipe DB as above.

## Environment notes for demo day

| Setting | Default | Demo impact |
| --- | --- | --- |
| `APP_AUTH_EMAIL_VERIFICATION_REQUIRED` | `false` | Login works right after registration |
| `APP_EMAIL_PROVIDER` | `log` | Verification emails logged, not sent (if you enable verification) |
| `APP_AI_ENABLED` / extraction provider | often `false` / `none` | Manual document review; say “AI extraction is off in this environment” |
| `APP_DEFAULT_PLAN` | `STARTER` | 3 users max, 10 clients — enough for admin + one business owner |

## After reset — what you will create live

1. Register firm + admin (UI)
2. Login (UI)
3. Optional: Firm settings currency/timezone (UI)
4. Categories (UI)
5. Client (UI)
6. Business owner user (UI) + **client access assignment (API — see main guide)**
7. Full bookkeeping → close (UI)

## Estimated prep time

| Task | Time |
| --- | --- |
| Fresh DB + start backend/frontend | 5–10 min |
| Full DB wipe + storage clear | 2–5 min |
| Quick smoke: register + login | 2 min |
