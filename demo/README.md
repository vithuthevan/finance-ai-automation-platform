# Demo day — Harbor Ledger Partners

## Start (recommended for today)

Docker full image build hit a Maven DNS issue on this machine, so use:

```powershell
# 1) Postgres only (set POSTGRES_PASSWORD=postgres in .env if backend uses local profile defaults)
docker compose up -d postgres

# 2) Backend (separate terminal)
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/finance_platform"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
# Set a strong secret locally — do not commit or display on slides:
$env:APP_JWT_SECRET="<your-local-jwt-secret-min-32-chars>"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:4200"
$env:APP_AUTH_COOKIE_SECURE="false"
$env:APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL="priya@harborledger.demo"
$env:APP_AI_ENABLED="false"
$env:APP_EMAIL_PROVIDER="log"
.\gradlew.bat :platform-app:bootRun

# 3) Frontend (separate terminal)
cd frontend
npm start

# 4) Seed
python demo/seed_demo.py
```

App URL: http://localhost:4200

## Logins (password for all: `DemoPass123!`)

| Role | Email |
|------|-------|
| ADMIN | `priya@harborledger.demo` |
| ACCOUNTANT | `nimal@harborledger.demo` |
| BUSINESS_OWNER | `amaya@cedarcafe.lk` |

Starter plan allows **3 users** (admin + accountant + owner).

Priya is also platform admin after backend restart with bootstrap email set (`/platform`).

## What is pre-seeded

- Firm **Harbor Ledger Partners** (LKR, Asia/Colombo, AI off)
- Client **Cedar Café (Pvt) Ltd**
- Categories: Food, Utilities, Bank, Rent, Café sales
- Users + client access + primary accountant (Nimal)
- Bank account **Cedar Café Operating**
- August approved income/expense (Trends not empty)
- Open document request: September rent invoice
- Keells receipt in Documents inbox (when upload succeeds)

## Sample files

- `demo/files/keells-receipt.pdf` (or `.png`)
- `demo/files/bank-sept.csv` — Banking → Import
- `demo/files/utility-bill.pdf` — owner rent request

## Live click path (start as Priya)

1. Documents → Review Keells → Accept as draft → Expenses → Approve  
2. Income → `2026-09-08` / Café sales / `18500` / Card settlement / Card → Approve  
3. Expenses → `2026-09-10` / Utilities / `6200` / CEB → Approve  
4. Banking → Import → `bank-sept.csv` → Preview → Import → Confirm matches (Ignore bank fee)  
5. Close → 2026 / September → Open workspace — readiness panel shows **1 open document request** (intentional)  
6. **Resolve before close** (pick one):
   - **Owner upload (recommended):** Logout → Amaya → upload `utility-bill.pdf` on rent request → Priya → Document Requests → **Complete**  
   - **Admin cancel:** Document Requests → Cancel “September rent invoice” with a short note  
7. Close → 2026 / September → Close period (blockers cleared)  
8. Reports → P&L + Trends  

> Period close correctly blocks while open document requests exist. Step 6 demonstrates that readiness check; do not skip it.

## Reset

```powershell
docker compose down -v
docker compose up -d postgres
# restart backend + frontend, then:
python demo/seed_demo.py
```
