# Architecture Mastery — Schema, Access Control & Security

How the database and `ClientAccessService` encode business rules that code alone could forget.

---

## 1. Tenancy is a column, not a database

**What:** Almost every firm-scoped table has `firm_id`.  
**Why:** One shared PostgreSQL for all firms (SaaS cost/ops model).  
**How:** JWT carries `firmId`; filter reloads user and checks match; services query with firm scope (e.g. `findByIdAndClient_IdAndFirmId`).  
**Alternative:** DB-per-tenant (strong isolation, painful ops) or Postgres RLS (defense in depth — not used yet).  
**Fail mode:** A new query that omits `firm_id` can leak cross-tenant data. Tests: `TenantIsolationIntegrationTest`.  
**Senior view:** Application-enforced tenancy is fine at early scale; treat “forgot firmId” as a P0 bug class.

Primary schema introduction: [`V2__tenancy_and_auth.sql`](../backend/platform-app/src/main/resources/db/migration/V2__tenancy_and_auth.sql)

```
firms
 ├── clients (uq firm_id+name, soft delete deleted_at)
 ├── users   (uq email, soft delete, role_id → roles)
 └── user_client_access (uq user+client, FULL|READ_ONLY|UPLOAD_ONLY)
```

Audit columns on entities via [`BaseEntity`](../backend/platform-core/src/main/java/com/finance/platform/core/domain/BaseEntity.java): `id`, `created_at`, `updated_at`, `created_by`, `updated_by`.  
Tenant column via [`TenantAwareEntity`](../backend/platform-core/src/main/java/com/finance/platform/core/domain/TenantAwareEntity.java).

---

## 2. Ledger schema as a state machine — V4

[`V4__ledger.sql`](../backend/platform-app/src/main/resources/db/migration/V4__ledger.sql)

- `expenses` / `income` with `status IN ('DRAFT','APPROVED','VOID')`.  
- CHECK constraints: APPROVED requires approver+timestamp; VOID requires voider+timestamp.  
- Evidence links: `expense_receipts` / `income_receipts` M:N.  
- Indexes for client+date, client+status, approved partial index for reporting.

**Invariant:** Reports and close readiness care about **APPROVED** rows; drafts are work-in-progress.

---

## 3. Banking & reconciliation — V13 (+ V21, V25)

[`V13__bank_and_reconciliation.sql`](../backend/platform-app/src/main/resources/db/migration/V13__bank_and_reconciliation.sql)

- `bank_imports` → `bank_transactions` → `reconciliation_matches` (bank line ↔ expense|income).

[`V25__correctness_and_reliability.sql`](../backend/platform-app/src/main/resources/db/migration/V25__correctness_and_reliability.sql) adds:

- Partial **unique** indexes so only one CONFIRMED match per bank txn / expense / income.  
- `row_version` on expenses, income, accounting_periods, bank_transactions.  
- `event_outbox` and `idempotency_keys` tables.

**Optimistic locking:** JPA `@Version` is mapped to `row_version` on those entities — concurrent updates yield HTTP 409 `CONCURRENT_MODIFICATION`.  
**Idempotency:** filter is on the security chain; UI sends `Idempotency-Key` on money POSTs.

---

## 4. Periods — V11

[`V11__accounting_periods.sql`](../backend/platform-app/src/main/resources/db/migration/V11__accounting_periods.sql)

- Unique `(firm_id, client_id, period_year, period_month)`.  
- Statuses: OPEN → IN_REVIEW → READY_TO_CLOSE → CLOSED (REOPENED for admin undo).  
- App layer: `PeriodCloseService.assertPeriodOpen` blocks approve/void/reconcile when CLOSED.

---

## 5. ClientAccessService — authorization beyond roles

File: [`ClientAccessService.java`](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/ClientAccessService.java)

| Method | Protects |
|--------|----------|
| `requireReadAccess` | Firm client + membership |
| `requireLedgerRead` | Blocks UPLOAD_ONLY from ledger |
| `requireWriteAccess` | FULL only; not AUDITOR; subscription write guard; client active |
| `requireUploadAccess` | Not READ_ONLY; not AUDITOR |
| `requireApproveAccess` | Write + ADMIN/ACCOUNTANT only |
| `requireReportAccess` | Not UPLOAD_ONLY |

**Role shortcuts:** ADMIN → effective FULL for all firm clients; AUDITOR → effective READ_ONLY.

**Why this exists:** `@PreAuthorize` is coarse (role on endpoint). Client assignment and access *type* are domain rules that must live next to data access.

**What can fail:** Endpoint that skips `ClientAccessService` and only uses `@PreAuthorize` can over-expose assigned-client data.

Product matrix: [`docs/SECURITY.md`](SECURITY.md).

---

## 6. Security model (condensed)

| Topic | Behavior |
|-------|----------|
| AuthN | Bearer JWT + hashed refresh; filter reloads user |
| AuthZ | Method security + ClientAccessService + platform grants |
| Passwords | BCrypt, min length 8 |
| CSRF | Disabled (Bearer header, not cookies) |
| CORS | Explicit origins; allows `Idempotency-Key`, `X-Request-Id` |
| Rate limit | Login in-memory only |
| Headers | Spring: nosniff, deny framing, referrer policy; HSTS/CSP at reverse proxy |
| Health | `/api/v1/health` liveness; `/api/v1/health/ready` checks DB (`503` if down) |
| AI | Drafts only; never auto-approve |
| Platform admin | `platform_admin_grants` — not firm ADMIN |

---

## Study checklist

- [ ] Draw firm → client → expense → receipt from V2+V4 without looking  
- [ ] Explain why CONFIRMED recon uniqueness is a partial index  
- [ ] Name three methods on `ClientAccessService` and who they reject  
- [ ] Find one place period close blocks a write  
