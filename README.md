# Finance Platform

Accountant-first Document-to-Close workspace for bookkeeping firms.

```
Collect → Extract → Review → Approve → Reconcile → Close → Report
```

```
finance-platform/
├── backend/     Spring Boot modular monolith (one JAR: platform-app)
├── frontend/    Angular 19 workspace
├── deploy/      Nginx and hosting configuration
└── docs/        Product and deployment notes
```

## Architecture

```
Angular frontend
        │
        ▼
Spring Boot modular-monolith REST API  (/api/v1)
        │
        ▼
    PostgreSQL
        +
Object storage / AI providers when configured
```

Modules (all compose into **one** backend process):

- `platform-core` — security context, audit, exceptions, storage, notifications
- `module-auth` — users, JWT, refresh tokens, client access
- `module-finance` — clients, categories, documents, ledger, close, bank
- `module-ai` — optional document extraction
- `module-reporting` — P&L, dashboard, exports
- `platform-app` — HTTP API and Flyway migrations

Tenancy is always the authenticated user's `firmId`.

## Local development

1. PostgreSQL 16, database `finance_platform`
2. Copy `.env.example` to `.env` and set `APP_JWT_SECRET`
3. Backend:

```
cd backend
./gradlew :platform-app:bootRun
```

4. Frontend (proxies `/api` to `http://localhost:8080`):

```
cd frontend
npm install
npm start
```

Registration does **not** return a JWT. Sign in after creating the firm.

Docker-style local stack (Postgres + backend + frontend):

```
docker compose up --build
```

Compose uses service DNS (`postgres`, `backend`), not localhost, between containers.

## Environment configuration

See [`.env.example`](.env.example). Important variables:

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_*` | PostgreSQL connection |
| `APP_JWT_SECRET` | JWT signing secret (required in `prod`) |
| `APP_CORS_ALLOWED_ORIGINS` | Browser origins for separate-host UI |
| `APP_STORAGE_PROVIDER` | `local` or `s3` |
| `APP_AI_*` / `DOCUMENT_EXTRACTION_PROVIDER` | Optional OCR/AI extraction. See [docs/AI.md](docs/AI.md) |
| `APP_EMAIL_PROVIDER` | `log` or `smtp` |

AI, S3, and SMTP are optional. The bookkeeping workflow remains usable without them.

## Deployment models

**Model A — same domain**

```
https://finance.example.com/      → Angular
https://finance.example.com/api   → Spring Boot
```

Frontend `assets/config.json` uses `{ "apiBaseUrl": "/api/v1" }`. Nginx config: `deploy/nginx/same-origin.conf`.

**Model B — separate domains**

```
https://app.example.com   → Angular
https://api.example.com   → Spring Boot
```

Set `apiBaseUrl` to `https://api.example.com/api/v1` and `APP_CORS_ALLOWED_ORIGINS` to the frontend origin.

The frontend is **not** copied into `src/main/resources/static`. Backend and frontend deploy independently.

Details: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Roles

- `ADMIN` — firm administration and full client access
- `ACCOUNTANT` — review, approve, reconcile, close assigned clients
- `AUDITOR` — read-only approved data, reports, audit log
- `BUSINESS_OWNER` — upload documents, see requests, simple approved totals

Client access types: `FULL`, `READ_ONLY`, `UPLOAD_ONLY`. Access type never exceeds role.

## API overview

Base path: `/api/v1`

- Auth, users, clients, categories
- Documents, expenses, income
- Bank, periods, document requests, month-end close work queue
- Reports, audit, firm, notifications
- Health: `/api/v1/health`, `/api/v1/health/ready`

## Documentation

- [Implementation status](docs/IMPLEMENTATION_STATUS.md)
- [Month-end close](docs/CLOSE.md)
- [Bank reconciliation](docs/BANK_RECONCILIATION.md)
- [Product roadmap](docs/PRODUCT_ROADMAP.md)
- [Deployment](docs/DEPLOYMENT.md)
