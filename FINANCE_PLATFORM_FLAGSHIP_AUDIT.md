# Finance Platform — Flagship SaaS Audit

**Audit date:** 2026-09-23  
**Scope:** Full repository inspection (backend modular monolith, Angular frontend, Flyway schema, CI, docs).  
**Method:** Trace workflows Frontend → API → Service → Persistence → Jobs/Integrations; do not assume features from folder names alone.

---

## Executive Summary

The codebase is a **multi-tenant, accountant-first Document-to-Close platform** built as a **Spring Boot modular monolith** (`platform-app`) with an **Angular 20** SPA. It solves **evidence-backed month-end bookkeeping** for accounting firms managing many SME clients: collect documents, optionally extract with AI, human review, draft/approve income and expense ledger entries, CSV bank import and reconciliation, evidence-driven period close, and approved-ledger reporting.

It is **not** a general ledger, ERP, or full double-entry accounting product. There is no chart of accounts, journal entries, trial balance, or balance sheet. Positioning is closest to:

**Bookkeeping workflow + document-to-close control plane** with an **embedded lightweight ledger** (approved income/expense by category).

**Flagship strengths already in code:** tenant-scoped JWT auth, `ClientAccessService` matrix, document lifecycle with durable **outbox-driven AI processing**, close readiness via pluggable `CloseCheck` beans, deterministic bank match scoring with explainable components, **PracticeWorkQueueService** / month-end command center, structured audit log, subscription quotas, integration tests for tenant isolation and golden paths, and newer AR/invoicing (V33–V35).

**Largest gaps vs. commercial flagship:** no AI Finance Copilot, no financial anomaly engine, external accounting integrations are **schema-only**, AI usage lacks per-request cost/token ledger, reconciliation is **Stage 1 (exact amount + heuristics)** only, several **P0-adjacent security/correctness** items remain from `docs/CRITICAL_CODE_REVIEW_FINDINGS.md`, CI often **skips Postgres/Testcontainers**, and observability stops short of distributed tracing and SLO-oriented alerting.

**Architecture recommendation:** Evolve the **existing modular monolith**—tighten financial integrity and tenant isolation first, unify operational UX around the work queue, then layer **proposal-based AI** and deterministic anomalies. **Do not** split microservices, add Kafka, Redis caching, or vector RAG unless a concrete scale or product requirement appears.

---

## Current Product Architecture

### Topology

```
Angular SPA (frontend/)
        │  HTTPS + cookies (refresh) / Bearer (access)
        ▼
platform-app REST  /api/v1
        │
        ├── platform-core    (tenant, audit, outbox, idempotency, storage, notifications)
        ├── module-auth      (JWT, sessions, users, registration)
        ├── module-finance   (clients, ledger, documents, bank, close, practice ops, AR)
        ├── module-ai        (extraction, classification, suggestions — optional providers)
        └── module-reporting (P&L, dashboards — read models)
        │
        ▼
PostgreSQL (Flyway V1–V35)
        +
Local/S3 file storage (firm/client-scoped keys)
        +
Optional OpenAI-compatible LLM / SMTP
```

### Backend modules

| Module | Responsibility |
|--------|----------------|
| `platform-core` | `TenantContext`, `AuditLogger`, `OutboxService` + `OutboxWorker`, `IdempotencyFilter`, `FileStorageService`, notification dispatch |
| `module-auth` | `JwtAuthenticationFilter`, roles, refresh tokens, rate limits, `UserFacade` |
| `module-finance` | Domain services: documents (`Receipt`), expenses/income, bank, `PeriodCloseService`, `CloseReadinessService`, `PracticeWorkQueueService`, chase, AR |
| `module-ai` | `DocumentAiProcessor`, `DocumentAiOutboxHandler`, providers (OpenAI/mock/disabled) |
| `module-reporting` | `ReportingFacade` / SQL aggregations on approved data |
| `platform-app` | Controllers, Flyway, health, platform admin |

### Frontend

- **Angular 20** feature folders under `frontend/src/app/features/`: dashboard, month-end command center, work queue, documents/review, expenses/income, banking, close, reports, AR, chase, admin, owner portal, platform ops.
- Guards: `authGuard`, `roleGuard`, `ledgerGuard`, `ownerGuard`, `platformGuard` (`auth.guard.ts`).
- API via `ApiService` with **stable idempotency keys** for high-risk POST paths (remediated from random UUID per click).

### Database (major entities)

- **Tenancy:** `firms`, `clients`, `users`, `user_client_access`
- **Ledger:** `expenses`, `income`, `categories` — statuses `DRAFT` / `APPROVED` / `VOID`
- **Evidence:** `receipts` (documents) with AI/review metadata; `document_processing_attempts`
- **Close:** `accounting_periods`, `document_requests`, `client_monthly_evidence_items`
- **Bank:** `bank_accounts`, `bank_imports`, `bank_transactions`, `reconciliation_matches`; V33 adds `reconciliation_match_groups` (partially used for invoice settlement flows)
- **Ops:** notifications, practice work assignments, client chase, subscriptions
- **AR:** customers, sales invoices, payments (V33–V35)
- **Reliability:** `event_outbox`, `idempotency_keys`
- **Placeholder:** `integration_connections`, `automation_rules` (V33) — **no Java application layer**

### Multi-tenancy model

- Tenant = **firm** (`firmId` on JWT + DB user reload).
- All client data is **firm-scoped** via `ClientJpaRepository.findByIdAndFirmId...` and `ClientAccessService.requireFirmClient`.
- **No PostgreSQL RLS** — isolation is application-enforced.
- **Platform admin** (`platform_admin_grants`) is intentional cross-firm operator surface.

### Authentication / authorization

- **Roles:** `ADMIN`, `ACCOUNTANT`, `AUDITOR`, `BUSINESS_OWNER`
- **Client access:** `FULL`, `READ_ONLY`, `UPLOAD_ONLY` capped by role
- **Central gate:** `ClientAccessService` — e.g. `requireLedgerWriteAccess` blocks `AUDITOR` and `BUSINESS_OWNER`; approve requires ADMIN/ACCOUNTANT
- Method security: `@PreAuthorize` on controllers

### Background processing

- **Outbox:** `DocumentService` enqueues `DOCUMENT_AI_PROCESS`; `OutboxWorker` polls; `DocumentAiOutboxHandler` processes
- **Schedulers:** subscription maintenance, overdue document requests, client chase
- **Not present:** Kafka, separate worker service, general-purpose job UI

### Deployment & CI

- Docker Compose, Nginx same-origin / split-host configs, Oracle/VPS docs
- CI: backend Gradle test+jar; frontend build; E2E on main (Playwright) — backend workflow **without** Docker service (Testcontainers suites may skip)

### Caching & storage

- **No** application data cache (appropriate for financial correctness)
- Redis optional for **auth rate limiting** only
- Files: `firms/{firmId}/clients/{clientId}/...`

---

## Current Business Workflow

### End-to-end (as implemented)

```
Document upload (owner/accountant)
    → checksum + store + audit
    → outbox: AI extract (if enabled & quota)
    → NEEDS_REVIEW / FAILED
    → accountant accept/modify/reject → DRAFT expense/income (source AI/MANUAL)
    → approve → APPROVED (reports include only APPROVED)
    → optional link evidence to approved lines
Bank CSV import → UNMATCHED lines
    → ReconciliationSuggestionService (scored, top 3)
    → confirm / reject / ignore / create DRAFT from bank
Period (calendar month) → CloseReadinessService (blockers/warnings)
    → start review → close (guards block writes to closed periods)
    → reports / exports on approved ledger
```

### Who uses it today

| Persona | Primary use |
|---------|-------------|
| Accounting firm ADMIN | Users, clients, firm settings, subscription, full client portfolio |
| ACCOUNTANT | Document review, approvals, bank recon, close, work queue |
| AUDITOR | Read-only approved data, reports, audit log (assigned clients) |
| BUSINESS_OWNER | Upload documents, respond to requests, simplified totals |
| Platform operator | Cross-firm grants, firm support (`/api/v1/platform/*`) |

### Mature vs. basic CRUD

| Area | Maturity |
|------|----------|
| Tenant auth + client access | **Mature** |
| Draft/approve/void ledger | **Mature CRUD** (not GL) |
| Document evidence + review | **Mature** |
| AI extraction pipeline | **Mature** (provider-dependent) |
| Close readiness engine | **Mature** (differentiator) |
| Bank CSV + 1:1 recon | **Mature** (Stage 1 matching) |
| Practice work queue / month-end CC | **Mature** |
| Reporting on approved data | **Mature** |
| Notifications + email abstraction | **Mature** in-app; email config-dependent |
| AR / sales invoices | **Newer but real** |
| Client chase automation | **Implemented** |
| External GL sync | **Absent** (schema only) |
| AI copilot / anomalies | **Absent** |
| Automation rules table | **Schema only** |

### Disconnected or thin features

- `integration_connections`, `automation_rules` — migrated, not wired to services
- `reconciliation_match_groups` — entity + partial use; UI/workflow still centered on 1:1 `reconciliation_matches`
- Feature entitlement strings on plans — metadata without deep product branching
- PDF reports, bank feeds, Textract — documented as out of scope / future

### What prevents “commercial finance SaaS” feel today

1. **Category positioning risk** — buyers expecting Xero/QuickBooks replacement will be disappointed (no GL).
2. **Operational hardening** — idempotency coverage, bank import races, session security gaps (see risks).
3. **No copilot / exception intelligence** — accountants still hunt across screens despite work queue.
4. **Integrations** — no export/sync to external ledgers.
5. **Observability & compliance story** — metrics exist; tracing, AI cost governance, and tamper-evident audit not flagship-grade.
6. **CI confidence** — green builds can skip deepest integration tests.

### Product category (honest)

**Primary:** Document-to-close / bookkeeping workflow platform for practices  
**Secondary:** Lightweight in-platform books + practice operations (work queue, chase, AR)  
**Not:** ERP, full accounting package, or banking aggregation product

---

## Existing Strengths

1. **Evidence-driven close** — `CloseCheck` plugin model, readiness API, closed-period write guards in services
2. **Practice operating layer** — `PracticeWorkQueueService`, portfolio close status, month-end command center
3. **Safe AI posture** — extraction creates **DRAFT** only; human accept/modify/reject with review outcomes on `Receipt`
4. **Explainable bank matching** — `MatchScoreComponentResponse` (amount, date, reference, description)
5. **Modular monolith boundaries** — clear modules, single deployable JAR, Flyway discipline
6. **Audit & activity** — rich `AuditAction` enum, activity feed from audit
7. **SaaS foundations** — plans, quotas, usage, platform admin separation
8. **Test investment** — tenant isolation, golden path, invoice-to-cash integration tests (when Postgres available)

---

## Major Weaknesses

1. No unified **financial work item** persistence — work queue is derived SQL (good for consistency, limits assignment/history unless `practice_work_assignments` expanded)
2. No **anomaly / exception** domain model
3. No **AI copilot** or tool-gated Q&A over finance data
4. **AI usage metrics** are counts/latency averages — not per-tenant token/cost/feature records (`AiExtractionFacadeImpl.usageMetrics`)
5. Reconciliation **requires exact amount match** for ledger candidates (`scoreLedger` returns 0 otherwise) — no fuzzy amount tier yet
6. **1:1** recon as primary UX; N:M groups immature
7. External integrations **not implemented** despite V33 tables
8. Reporting lacks close-readiness / exception / approval-ageing as first-class reports
9. Frontend test surface minimal; many UX edge cases (expired JWT guard, multi-tab refresh) documented as open
10. Some **schema ahead of code** creates false impression of capability (integrations, automation)

---

## Architecture Risks

| Risk | Detail |
|------|--------|
| Application-layer tenancy only | Missing `firmId` on one code path → IDOR; needs systematic automated tests per resource |
| Native SQL table name parameters | `CloseReadinessQueryRepository` / reporting — constants today; allowlist not enforced in API |
| Work queue N+1 / per-client readiness | `PeriodCloseService.workQueue` and portfolio scale with client count |
| Outbox single-process worker | Acceptable at current scale; worker crash mid-handler needs idempotent handlers |
| Modular leakage | Reporting and finance share DB views; acceptable in monolith but watch cross-module SQL |
| Event coupling | Domain events + listeners exist; not all side effects migrated from direct calls |

**Explicit non-recommendations:** Microservices, Kafka, Kubernetes, Redis cache layer, vector DB for copilot v1.

---

## Financial Integrity Risks

| Item | Severity | Notes |
|------|----------|-------|
| Bank import concurrent duplicate rows | High | App-level dedupe; V31 uniqueness partial — race under concurrent imports |
| Idempotency scope | High | Improved on frontend; filter still method+URI hash; many POSTs outside protected regex |
| Post-close evidence graph changes | High | Linking/unlinking on APPROVED allowed — changes “document support” after close |
| Period close TOCTOU | Medium | Remediated with locking per critical review — verify in `PeriodCloseService` under load |
| Duplicate drafts from documents | Medium | Remediated with guards + `@Version` on `Receipt` — regression tests needed |
| Bank line → multiple drafts | High | `createExpenseFromBank` path — match status guards called out in review |
| Money in Excel export | Medium | POI numeric cells — presentation scale 2; ledger uses `BigDecimal` in DB |
| No double-entry invariant | Inherent | Product choice — mis-set expectations with buyers |

---

## Multi-Tenancy / Security Risks

### P0 (address before flagship demo to security-conscious buyers)

- Exhaustive **IDOR integration tests** for every `{id}` route (expense, income, document, bank, period, AR, match)
- **Bank CSV upload** validation weaker than document upload (size/MIME/magic)
- **Registration abuse** — open firm+ADMIN creation without verification/CAPTCHA/rate limit breadth
- **Session hygiene** — password change/reset vs refresh/access token revocation (partially documented as open)

### P1 (hardening)

- Auth rate limit coverage (register/forgot/refresh) and **429** responses
- Nginx **security headers** vs `SECURITY.md` claims
- **Category load by id** without firm scope in AI persistence (defense in depth)
- Platform admin bootstrap email env on every startup
- Public `/health/ready` DB probe

### P2

- Refresh token reuse detection family revocation
- Stronger password policy
- CSP tuning for Angular

**Recommendation:** Add `TenantIsolationParameterizedTest` scanning OpenAPI paths + generated IDOR matrix; run in CI with Postgres service.

---

## UX / Product Problems

- Dashboard mixes useful ops cards with generic stats — **month-end** and **work** are the right direction but not yet a single “command centre”
- Document pipeline status visible in inbox but owners may lack progressive disclosure on failures
- Banking: no reconciliation export; import profile CRUD thin
- Reports: no drill-down from P&L row to filtered transactions in all views (API supports filters; UX inconsistent)
- Role documentation vs code drift (BUSINESS_OWNER ledger write was doc issue; code blocks via `requireLedgerWriteAccess` — keep docs aligned)
- Duplicate navigation paths (close vs month-end vs work) — related but not unified mental model
- Limited empty/loading/error state standardization across features

---

## AI Opportunities

### Current AI

- Async document extraction + accounting category suggestion
- Historical vendor/customer hints before LLM
- Review outcomes: ACCEPTED / MODIFIED / REJECTED / MANUAL stored on receipt metadata
- Firm toggle `aiEnabled`; subscription quota skip with manual fallback
- Admin metrics endpoint — processed/success/failed/accept/modify/reject counts

### Gaps for flagship

- **No copilot** orchestration layer
- **No structured AI recommendation entity** (model, confidence, reasoning, user decision) for audit/feedback loop
- **No per-request `AiUsageRecord`** (tokens, cost, feature, latency)
- **No RAG** — not required for v1 copilot if tools query structured data; RAG justified later for uploaded PDF text and firm policy docs
- Bank statement line extraction not implemented

### Safe AI Finance Copilot (target design)

```
User question
  → Copilot orchestrator (module-ai or new module-copilot)
  → Tool router (allowlisted)
  → Application services (tenant-scoped)
  → Structured answer + citations (entity ids, period, counts)
```

**Tools (map to existing services):**

| Tool | Existing anchor |
|------|-----------------|
| `getPeriodSummary` | `CloseReadinessService` + reporting facade |
| `getUnreconciledTransactions` | `BankTransactionJpaRepository` + banking service |
| `getCloseBlockers` | `PeriodCloseService` / readiness DTO |
| `getExpenseVariance` | `ReportingQueryRepository` comparison |
| `getSupplierSpend` | expense aggregations by vendor |
| `searchDocuments` | `DocumentService.list` / inbox queries |
| `getApprovalQueue` | `PracticeWorkQueryRepository` |
| `getWorkSummary` | `PracticeWorkQueueService.summary` |

**No arbitrary SQL from LLM.** Read-only tools for AUDITOR role.

### AI action proposals (target)

Extend document review UI pattern: show suggestion card with Approve/Modify/Reject; persist `AI_CATEGORY_PROPOSED` / `AI_CATEGORY_ACCEPTED` audit events with payload JSON.

---

## Close-Readiness Assessment

**Current:** Real engine — not a status flag only.

- API: period readiness with blockers, warnings, checklist, ledger/document/bank summaries
- Checks: drafts, document review, unlinked financial docs, open requests, unsupported approved (warning), bank import presence (warning), bank reconciliation incomplete (blocker)
- Close re-evaluates readiness; closed periods guarded on mutations
- Work queue surfaces “ready to close” counts

**Gaps:**

- No persisted close **snapshot** at close time (live queries over closed data)
- Firm policy toggles (e.g. unsupported approved → blocker) not exposed in UI
- Portfolio view exists via APIs but needs stronger “client health” UX
- Fiscal year / non-calendar periods not configured

**Target API shape** (largely already present): `GET .../periods/{id}/readiness` — extend with `score` alias and typed blocker enums for copilot.

---

## Reconciliation Assessment

**Implemented (Stage 1 — deterministic):**

- Exact amount match required for expense/income suggestions
- Date window ±7 days for candidate search
- Scoring components exposed to UI
- Directions: debit → expense; credit → income + issued sales invoices (outstanding)
- 1:1 confirmed matches; `ReconciliationMatchGroup` for some invoice payment confirmations

**Not implemented:**

- Fuzzy amount (FX, fees, partial payments)
- 1:N / N:1 matching as primary workflow (schema hints in V33)
- Stage 3 AI-assisted match (only heuristic text overlap today)
- Opening/closing balance validation
- Bank feeds

**Staged engine recommendation:**

| Stage | Behavior |
|-------|----------|
| 1 | Exact amount + date/reference/text (current) |
| 2 | Weighted fuzzy amount tiers, partial payment rules |
| 3 | AI suggests with **mandatory** component breakdown (never opaque score) |
| 4 | Human confirm — always |

---

## Reporting Assessment

| Report | User | Decision | Source | Reconciles to ledger |
|--------|------|----------|--------|----------------------|
| P&L | Accountant, owner (simplified) | Profitability | Approved expenses/income | Yes — APPROVED only |
| Income/expense summaries | Accountant | Category/method mix | SQL aggregates | Yes |
| Trends | Accountant | MoM direction | Monthly buckets | Yes |
| Practice dashboard | Firm admin | Workload | Counts, not cross-client money sum | N/A |
| Cash movement | Accountant | Liquidity proxy | Income − expense | Labeled non-IFRS |
| AR aging | Practice | Collections | Invoice/payment tables | AR subledger |

**Missing flagship reports:** close-readiness export, unreconciled items, approval ageing, anomaly/exception register, supplier spike report.

**Remove/redesign:** None critical — avoid adding GL reports that imply double-entry.

---

## Observability Assessment

**Present:**

- Spring Boot Actuator + Prometheus registry
- `BusinessMetricsCollector`, outbox worker metrics
- MDC: requestId, firmId, userId (`TenantMdcFilter`)
- Custom health endpoints

**Missing:**

- OpenTelemetry traces
- Standard dashboards (5xx, latency, AI failure rate, import failure, email failure)
- AI token/cost metrics
- Correlation from HTTP → outbox → AI attempt

**Health checks:** Add dependency checks for storage, AI provider reachability (optional), SMTP.

---

## Production Reliability Assessment

| Pattern | Where needed | Current |
|---------|--------------|---------|
| Outbox | AI, notifications | **Implemented** |
| Idempotency | Approve, close, import | **Partial** |
| Retry | AI provider | Timeouts + retry cap on documents |
| Circuit breaker | External AI | Not Resilience4j — acceptable short-term |
| Optimistic locking | Receipt, expenses | Partial — gaps on some entities |
| DLQ | Failed outbox | Investigate failed row handling / replay UI |
| Pessimistic lock | Subscription quota | Implemented |

---

## Proposed Flagship Architecture

Keep **modular monolith**; add **vertical slices** inside `module-finance`:

```
┌─────────────────────────────────────────────────────────────┐
│                     API (platform-app)                       │
├─────────────────────────────────────────────────────────────┤
│  WorkOrchestration │ Close │ BankRecon │ Documents │ Ledger │
│  Anomalies (new) │ CopilotTools (new) │ Integrations (new)│
├─────────────────────────────────────────────────────────────┤
│  platform-core: audit, outbox, tenant, idempotency         │
│  module-ai: extraction + (future) tool-calling adapter       │
│  module-reporting: read models                               │
└─────────────────────────────────────────────────────────────┘
```

**Workflow orchestration:** Do not add a global state machine framework. Instead:

- **Document:** existing `ReceiptStatus` + processing attempts
- **Ledger:** `TransactionStatus`
- **Bank:** `MatchStatus` + `ReconciliationMatch` status
- **Period:** `AccountingPeriod` lifecycle
- **Unify in Work Queue** as cross-entity “attention” projection

Explicit state machine only if adding long-running sagas (e.g. multi-step external sync) — not yet.

---

## Proposed Product Workflow

Target narrative (evolution, not rewrite):

```
UPLOADED → QUEUED/PROCESSING → EXTRACTED/NEEDS_REVIEW → VALIDATED (draft created)
→ APPROVED → MATCHED (bank) → RECONCILED (period) → CLOSED
```

Map to existing enums; add `VALIDATED` as derived (“draft exists from document”) rather than new DB status if possible.

**Human gates:** review, approve, match confirm, close.

---

## Work Queue Design

**Current:** Derived items via `PracticeWorkQueryRepository` — types include document review, processing failure, transaction approval, document requests, bank unresolved, period close.

**Enhancements (P1):**

| Field | Source |
|-------|--------|
| Priority | request priority, overdue flags, blocker severity |
| Client | all rows |
| Type | existing enums |
| Description | query labels |
| Age | created/received timestamps |
| Assigned To | `practice_work_assignments` + primary accountant |
| Status | OPEN / IN_PROGRESS |
| Action | deep link route |

Add **ANOMALY** and **FAILED_IMPORT** types when engines exist.

Single UI: `/app/work` as default landing for accountants (optional product decision).

---

## AI Finance Copilot Design

- **Module:** extend `module-ai` with `CopilotService` + `FinanceToolRegistry`
- **Auth:** same JWT; tools call `ClientAccessService` per clientId argument
- **Persistence:** conversation optional; log prompts/responses in audit with redaction
- **RAG:** defer; use document full-text search in Postgres or object storage index only when policy Q&A is required

---

## Anomaly Detection Design

**New table:** `financial_anomalies` (per user spec)

**Phase 1 — deterministic rules only:**

| Type | Rule |
|------|------|
| DUPLICATE_EXPENSE | same vendor+amount+date window |
| LARGE_TRANSACTION | > N × rolling median |
| SUPPLIER_SPIKE | MoM spend % threshold |
| REUSED_INVOICE_NO | duplicate reference |
| UNRECONCILED_STALE | bank line age > threshold |
| VARIANCE | vs prior period from reporting SQL |

Run on: approve, import complete, close readiness evaluation (async via outbox if heavy).

Surface in work queue + period readiness warnings.

---

## Integration Architecture

**Port interface (new package `integration`):**

```java
public interface AccountingProvider {
  ConnectionTestResult testConnection();
  SyncResult pushExpense(...);
  SyncResult pushIncome(...);
  // future: fetchAccounts, pushCreditNote
}
```

**Adapters:** none in v1 — manual CSV export sufficient for Phase 7

**Principles:**

- Local ledger remains source of truth until push confirmed
- Idempotent external keys stored on `integration_connections` row
- Failures → `FAILED` sync status + notification; never roll back approved local state

Wire V33 `integration_connections` to entities before marketing integrations.

---

## Maturity Scorecard

Scores 0–5 (current state, honest).

| Dimension | CURRENT | TARGET | GAP | RECOMMENDED ACTION |
|-----------|---------|--------|-----|---------------------|
| Domain modelling | 3.5 | 4.5 | Medium | Anomaly entity; AI recommendation record; integration sync model |
| Backend architecture | 4.0 | 4.5 | Low | Tool registry; tighten module APIs |
| API quality | 3.5 | 4.5 | Medium | ProblemDetails consistency; idempotency expansion; OpenAPI CI |
| Frontend quality | 3.0 | 4.0 | Medium | Shared ops components; command centre UX |
| Database design | 4.0 | 4.5 | Low | Indexes for anomalies/audit; unique constraints on bank rows |
| Multi-tenancy | 3.5 | 5.0 | High | Automated IDOR matrix in CI |
| Authorization | 4.0 | 4.5 | Low | Capability model optional; doc alignment |
| Security | 3.0 | 4.5 | High | Close P0/P1 from critical review |
| Auditability | 3.5 | 4.5 | Medium | AI decision payload; close snapshot optional |
| Reconciliation | 3.0 | 4.5 | High | Fuzzy + partial; explainable stages |
| Document intelligence | 4.0 | 4.5 | Low | Pipeline status UX; virus scan hook |
| AI maturity | 2.5 | 4.5 | High | Copilot tools; usage cost records |
| Reporting | 3.5 | 4.0 | Medium | Ops reports (close, recon, ageing) |
| Observability | 2.5 | 4.0 | High | OTel + dashboards + SLO alerts |
| Reliability | 3.5 | 4.5 | Medium | Idempotency + outbox replay + DB constraints |
| Cloud readiness | 3.5 | 4.0 | Low | S3 prod path; secrets management docs |
| Testing | 3.0 | 4.5 | High | CI Postgres; frontend tests; IDOR suite |
| CI/CD | 2.5 | 4.0 | High | Docker in backend workflow; npm test |
| UX | 3.0 | 4.5 | High | Work-first navigation; consistent tables |
| Commercial readiness | 3.0 | 4.5 | High | Integrations truth; sales positioning doc |

---

## P0 / P1 / P2 / P3 Roadmap (Summary)

### P0 — Fundamental / dangerous

- Tenant isolation test matrix (all resources)
- Bank import dedupe DB constraints + match-status guards on bank→ledger creates
- Idempotency coverage for bank/document/close/reopen paths
- Align security docs with code; fix auth rate limit gaps
- CI: run Postgres integration tests on every PR

### P1 — Flagship foundation

- Unified **command centre** (work queue + close blockers + portfolio health)
- Close-readiness UX parity with backend rules
- Reconciliation Stage 2 (partial/fuzzy) with component breakdown
- Audit enhancements for AI proposals
- Observability baseline (metrics catalog + alert rules)
- `financial_anomalies` v1 (deterministic)

### P2 — Intelligent platform

- AI action proposal framework + feedback dataset
- Copilot with allowlisted tools (read-only first)
- `AiUsageRecord` per request
- Document pipeline hardening (optional AV scan)

### P3 — Scale / commercial expansion

- Accounting provider adapters (Xero/QBO/Sage)
- Bank feeds
- Match groups N:M UX
- Advanced analytics; multi-region only if demand

---

## Recommended First Implementation Phase

**Phase 1 (see `FINANCE_PLATFORM_FLAGSHIP_ROADMAP.md`):** Financial correctness + tenant/security foundation — no new features until green IDOR suite, bank integrity constraints, idempotency expansion, and CI Postgres gate.

---

## Product Differentiation (5–10 defensible)

1. **Evidence-driven close** with explicit blockers tied to real queries
2. **Practice work queue** derived from books — not a generic task app
3. **Explainable bank matching** (component scores, not black-box AI)
4. **Controlled AI** — drafts only, review outcomes auditable
5. **Multi-client portfolio close readiness** for firms
6. **Client chase** tied to document requests
7. **Owner upload portal** without ledger access leakage
8. **Subscription-aware operations** without silencing manual workflow when AI quota exhausted

---

## Architecture Maturity

**Modular monolith remains appropriate** for foreseeable SME/practice scale. Potential future boundaries (only if operational pain):

- Document/AI worker pool (same codebase, scaled deployment)
- Integration sync worker (rate limits, retries)

No microservice split until team or deploy conflict demands it.

---

## Readiness Percentages

| Milestone | % |
|-----------|---|
| **CURRENT FLAGSHIP READINESS** | **58%** |
| **TARGET AFTER P1** | **72%** |
| **TARGET AFTER P2** | **85%** |

_Rationale: Strong workflow core and close/recon foundations; flagship narrative requires security proof, ops observability, anomalies, and copilot with cost governance._

---

## Appendix: Key File References

| Concern | Location |
|---------|----------|
| Work queue | `PracticeWorkQueueService.java`, `WorkController` |
| Close readiness | `CloseReadinessService.java`, `application/close/*Check.java` |
| Bank matching | `ReconciliationSuggestionService.java` |
| Document + outbox | `DocumentService.java`, `OutboxWorker.java` |
| Tenant access | `ClientAccessService.java`, `JwtAuthenticationFilter.java` |
| Audit | `AuditAction`, `AuditController` |
| Reporting | `module-reporting`, `ReportingController` |
| AI | `module-ai`, `DocumentAiOutboxHandler` |
| Frontend routes | `frontend/src/app/app.routes.ts` |
| Migrations | `backend/platform-app/src/main/resources/db/migration/` |
