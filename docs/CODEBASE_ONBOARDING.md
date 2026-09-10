# Finance Platform — Deep Codebase Onboarding

Senior-engineer orientation for [finance-ai-automation-platform](../README.md). Paths are relative to the repo root. Evidence cites real types and methods.

---

## 1. High-level overview

**What it is:** An accountant-first **Document-to-Close** workspace for bookkeeping / accounting firms. Product pipeline from [`README.md`](../README.md):

```
Collect → Extract → Review → Approve → Reconcile → Close → Report
```

**Who it is for:**

| Actor | Intent |
|-------|--------|
| Firm **ADMIN** | Firm setup, users, clients, categories, subscription, reopen closed periods |
| **ACCOUNTANT** | Review docs, approve ledger, bank recon, close assigned clients |
| **AUDITOR** | Read-only approved/voided data, reports, audit log |
| **BUSINESS_OWNER** | Upload evidence, respond to document requests, see approved totals |
| **Platform admin** | Cross-firm SaaS ops (plans, trials, suspend) — separate from firm `ADMIN` |

**Problem solved:** Firms drowning in receipts/invoices and month-end chaos get one multi-tenant system to collect evidence, optionally AI-suggest drafts, human-approve books, reconcile bank CSVs, close periods with readiness gates, and report — without auto-posting AI into the ledger.

**Tenancy rule:** Everything is scoped to the authenticated user's `firmId`. Client-level access is further constrained by `UserClientAccess` (`FULL` / `READ_ONLY` / `UPLOAD_ONLY`). Access type never exceeds role ([`docs/SECURITY.md`](SECURITY.md)).

**What it is not:** Not a payment processor (no Stripe), not a full GL/ERP (no Balance Sheet / Trial Balance), not microservices. AI never auto-approves.

Phases 1–8 are complete per [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md): foundation → documents → reports → AI → close → bank → practice workflow → SaaS.

---

## 2. Tech stack & key dependencies

| Layer | Choice | Why (from code usage) |
|-------|--------|------------------------|
| Backend language | **Java 17** | Gradle multi-module Spring Boot app |
| Backend framework | **Spring Boot 4.x** modular monolith | One process (`platform-app`), six Gradle modules |
| Security | Spring Security + **JJWT 0.12.6** | Stateless access JWT + DB-backed refresh tokens |
| Persistence | **Spring Data JPA** + **PostgreSQL 16** + **Flyway** | Migrations `V1`…`V25` under `backend/platform-app/src/main/resources/db/migration/` |
| API docs | **springdoc OpenAPI** | Swagger on `local` profile |
| Object storage | **AWS SDK S3 2.x** or local FS | `FileStorageService` provider switch |
| AI | JDK HTTP → OpenAI-compatible `/chat/completions` | Optional; `none` / `mock` / `openai` modes |
| Email | Spring Mail / log provider | Optional SMTP; default logs intent |
| Excel export | **Apache POI OOXML** | Report XLSX in `module-reporting` |
| Frontend | **Angular 19** + **Angular Material** + RxJS | Lazy-loaded standalone pages |
| Tests | Testcontainers (+ skip without Docker), Vitest | Tenant/JWT/upload/quota integration tests |
| Ops | Docker Compose, Nginx, Caddy | [`docker-compose.yml`](../docker-compose.yml), [`deploy/`](../deploy/) |

**Gradle modules** ([`backend/settings.gradle`](../backend/settings.gradle)):

- `platform-core` — tenant base entities, audit, exceptions, storage, notifications, idempotency, outbox
- `module-auth` — users, JWT, sessions, client access
- `module-finance` — clients, docs, ledger, bank, close, subscriptions
- `module-ai` — extraction after document upload
- `module-reporting` — aggregates + CSV/XLSX
- `platform-app` — HTTP controllers, Flyway, `FinancePlatformApplication`

---

## 3. Architecture

### Overall pattern

**Modular monolith:** HTTP adapters in `platform-app`, domain + application services in feature modules, shared kernel in `platform-core`. Single deployable JAR; module boundaries are package/Gradle, not network.

```
Angular 19 SPA
      │  Bearer JWT
      ▼
platform-app controllers  (/api/v1)
      ▼
module-* application services  (@Transactional)
      ▼
JPA entities → PostgreSQL
      +
FileStorage (local|S3) · optional AI · optional SMTP
      +
Spring @Async / @Scheduled (in-process)
```

### Top-level directories

| Path | Responsibility |
|------|----------------|
| `backend/` | All server code + migrations |
| `frontend/` | Angular SPA |
| `docs/` | Architecture, AI, close, bank, SaaS, security |
| `deploy/` | Nginx/Caddy hosting |
| `postman/` | API collection |
| `.github/workflows/` | CI for backend/frontend |
| Root | `README`, `docker-compose`, `.env.example`, QA scripts |

### Data flow (typical write)

1. Angular feature page → `ApiService` → `POST /api/v1/...` (+ optional `Idempotency-Key`)
2. `JwtAuthenticationFilter` validates JWT, reloads `User` from DB, checks `user.firmId` equals token claim, sets `TenantContextHolder`
3. Thin controller (`@PreAuthorize`) → application service
4. Service: access checks, subscription quotas, domain rules → JPA save
5. Side effects: `AuditLogger`, Spring domain events → `@Async` AI / workflow notifications
6. `GlobalExceptionHandler` → RFC7807 `ProblemDetail` + `errorCode`
7. DTO response (entities with `passwordHash` are never serialized)

### Key design patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **TenantAwareEntity** | `platform-core/.../TenantAwareEntity.java` | Every firm-scoped row carries immutable `firmId` |
| **TenantContext** | `TenantContextHolder` ThreadLocal | Set by JWT filter; used by audit, idempotency, JPA `@CreatedBy` |
| **Ports/facades** | e.g. `FirmStaffPort` → `UserFacadeImpl` | Cross-module coupling without circular Gradle deps |
| **Status state machine** | `TransactionStatusRules` | DRAFT / APPROVED / VOID transitions centralized |
| **Optimistic locking** | `@Version` on `Expense`, `AccountingPeriod`, etc. | Concurrent approve/close → HTTP 409 |
| **Idempotency filter** | `IdempotencyFilter` + `IdempotencyService` | Safe retries on approve/close/bank import/AI accept |
| **After-commit async** | `DocumentUploadedListener` | AI never blocks upload transaction |
| **Human-in-the-loop AI** | `DocumentReviewService.accept` | Creates **DRAFT** only (`source=AI`) |
| **Manual billing** | `ManualBillingProvider` / platform APIs | SaaS quotas without payment gateway |
| **Pluggable close checks** | `CloseCheck` implementations | Readiness blockers/warnings recomputed on every close |

---

## 4. Core domain models / entities

**Base:** `BaseEntity` (UUID id, created/updated/by) → `TenantAwareEntity` (+ `firmId`). `Firm` extends `BaseEntity` only (tenant root).

### Auth (`module-auth/.../domain/model/`)

| Entity | Table | Key fields |
|--------|-------|------------|
| `Role` | `roles` | ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER |
| `User` | `users` | email, passwordHash, fullName, active, `role`, `firmId`, `clientAccesses` |
| `UserClientAccess` | `user_client_access` | user ↔ client, FULL / READ_ONLY / UPLOAD_ONLY |
| `RefreshToken` | `refresh_tokens` | hashed token, expiry, revoked |
| `PasswordResetToken` | `password_reset_tokens` | one-time reset |

### Finance (`module-finance/.../domain/model/`)

| Entity | Purpose |
|--------|---------|
| `Firm` | name, currency (`LKR`), timezone (`Asia/Colombo`), FY start month, `aiEnabled` |
| `Client` | bookkeeping client; optional primary accountant |
| `Category` | income/expense categories |
| `Receipt` | **documents** (table `receipts`); storageKey, checksum, status lifecycle, embedded `AiExtractionMetadata` |
| `Expense` / `Income` | ledger: amount, date, category, DRAFT/APPROVED/VOID, M:N receipts via join tables |
| `AccountingPeriod` | per client year/month; OPEN → IN_REVIEW → CLOSED / REOPENED |
| `DocumentRequest` | ask client for missing evidence |
| `BankAccount`, `BankImport`, `BankTransaction`, `ReconciliationMatch`, `BankImportProfile` | CSV bank recon |
| `SubscriptionPlan`, `FirmSubscription`, `PlanChangeRequest` | SaaS plans/quotas |

### Platform / core

| Entity | Purpose |
|--------|---------|
| `AuditLog` | immutable activity trail |
| `Notification`, `NotificationPreference`, `NotificationDelivery` | in-app (+ email intent) |
| `IdempotencyKey` | replay protection |
| `EventOutbox` | outbox rows present; **no dedicated poller worker** |
| `PlatformAdminGrant` | SaaS operator privilege |

**Document statuses:** `UPLOADED → PROCESSING → EXTRACTED | NEEDS_REVIEW → LINKED | REJECTED | FAILED`

**Ledger rules** (`TransactionStatusRules`):

- Only DRAFT editable/deletable
- Only DRAFT → APPROVED
- Only APPROVED → VOID (drafts are deleted, not voided)
- Auditors see finalized only (`isFinalized`)

---

## 5. Entry points & request flows

### Entry points

| Entry | Path |
|-------|------|
| Backend main | `backend/platform-app/.../FinancePlatformApplication.java` |
| Frontend | `frontend/src/main.ts` → `app.routes.ts` |
| API base | `/api/v1` — controllers under `backend/platform-app/.../controller/` |

### Flow A — Login

1. `frontend/.../login.page.ts` `submit()` → `AuthService.login()`
2. `POST /api/v1/auth/login` → `LoginController.login`
3. `AuthenticationService.login`: `AuthRateLimiter.checkAllowed` → `AuthenticationManager.authenticate` → load `User`
4. `JwtService.issueAccessToken` + `SessionService.issueRefreshToken` → persist `RefreshToken`
5. `AuditLogger` `LOGIN_SUCCESS` → `LoginResponse` (access + refresh + `uploadOnlyWorkspace` flag)
6. Frontend stores session in `localStorage` (`fp.session`); may call `GET /api/v1/platform/me`

**Tenant-scoped read example:** `ExpenseService.get` / `findExpense` uses:

```java
expenseRepository.findByIdAndClient_IdAndFirmId(expenseId, clientId, firmId)
```

JWT alone is insufficient: claim `firmId` must match the DB user (`JwtAuthenticationFilter` lines 58–61), and repositories always filter by firm.

### Flow B — Document upload → AI → accept → approve

1. UI (`documents.page` / `owner.page`) → `POST /clients/{id}/documents` multipart  
   → `DocumentController.upload` → `DocumentService.upload`
2. `requireUploadAccess` + subscription quota → MIME/extension validation → SHA-256 duplicate check  
   → store at `firms/{firmId}/clients/{clientId}/documents/{uuid}`  
   → save `Receipt` (status `UPLOADED`) → publish `DocumentUploadedEvent` → audit `DOCUMENT_UPLOADED`  
   → on persist failure, best-effort `fileStorageService.delete`
3. After commit (`DocumentUploadedListener`):
   - `@Async` → `DocumentAiProcessor.process` → extraction provider → update AI metadata / status  
   - `WorkflowNotificationListener` → in-app notification
4. Review UI → `POST .../review/accept` (+ Idempotency-Key)  
   → `DocumentReviewService.accept` → `createDraft(..., TransactionSource.AI)` → DRAFT Expense/Income + link Receipt → status `LINKED`
5. Accountant → `POST .../expenses/{id}/approve` → `ExpenseService.approve`  
   → `TransactionStatusRules.assertCanApprove` + `assertPeriodOpen` → `expense.approve(user)` → audit `EXPENSE_APPROVED`  
   → amount now appears in reports (`APPROVED` only)

### Flow C — Bank import → confirm match → close period

1. `banking.page` → preview `POST .../bank/imports/preview` (no persist)  
   → import `POST .../bank/imports` → `BankReconciliationService.importCsv`  
   → checksum duplicate guard → store CSV → `BankImport` + `BankTransaction` rows → `generateSuggestions` (deterministic scoring)
2. Confirm `POST .../transactions/{id}/confirm` → `confirmMatch` → `ReconciliationMatch` CONFIRMED + bank `MATCHED` (requires APPROVED ledger amounts)
3. `close.page` → `POST .../periods?year&month` → `PeriodCloseService.getOrCreate`
4. `GET .../readiness` → pluggable `CloseCheck`s (drafts, docs needing review, unlinked financial docs, open requests, unmatched bank)
5. `POST .../review` → `IN_REVIEW`; `POST .../close` re-evaluates readiness — if blockers → `PERIOD_NOT_READY_TO_CLOSE`; else `CLOSED` + notify + audit
6. ADMIN `POST .../reopen` with reason → `REOPENED`

---

## 6. External integrations

| Integration | Config | Called from |
|-------------|--------|-------------|
| **PostgreSQL** | `SPRING_DATASOURCE_*` | All JPA; `GET /health/ready` pings DB |
| **Local FS / S3** | `APP_STORAGE_PROVIDER=local\|s3` | `FileStorageService` on document/bank CSV upload/download/delete |
| **OpenAI-compatible LLM** | `APP_AI_*`, `DOCUMENT_EXTRACTION_PROVIDER` | `OpenAiCompatibleExtractionProvider` via `DocumentAiProcessor` |
| **SMTP / log email** | `APP_EMAIL_PROVIDER`, `MAIL_*` | Password reset, workflow/overdue reminders |
| **Manual billing** | Plan catalog in DB + platform APIs | Upgrade *requests* only — no Stripe |
| **OpenAPI** | springdoc (local) | Dev discovery |

**Schedulers (in-process Spring):**

- `SubscriptionMaintenanceScheduler` — expire trials → SUSPENDED; advance billing period; trial/usage notices
- `OverdueDocumentRequestScheduler` — remind open overdue document requests

AI, S3, and SMTP are optional; bookkeeping works without them ([`docs/AI.md`](AI.md)).

---

## 7. Notable conventions & patterns

**Naming:** Controllers `*Controller`, services `*Service`, JPA `*JpaRepository`, DTOs `*Request`/`*Response`/`*View`. API/UI say “document”; entity/table remains `Receipt` / `receipts` (intentional reuse — do not invent a parallel Document table).

**Error handling:** `GlobalExceptionHandler` → `ProblemDetail` + stable `errorCode` (`ErrorCodes.*`). Optimistic lock → 409. Possible duplicate document → 409 with `existingDocumentId`.

**Security:** Method `@PreAuthorize` on controllers; JWT filter reloads user; `spring.jpa.open-in-view: false` forces fetch planning in services. CSRF disabled (Bearer header, not cookies).

**Config:** Env → Spring `app.*` ([`.env.example`](../.env.example)); frontend runtime `frontend/src/assets/config.json` (`apiBaseUrl`).

**Testing:** Integration-heavy under `platform-app/src/test` — `TenantIsolationIntegrationTest`, `JwtSecurityIntegrationTest`, `FileUploadSecurityIntegrationTest`, `SubscriptionQuotaConcurrencyIntegrationTest`, Flyway tests. Frontend Vitest. Postgres Testcontainers skip when Docker unavailable.

**Audit:** Mutating finance actions emit typed events (`DOCUMENT_UPLOADED`, `EXPENSE_APPROVED`, `PERIOD_CLOSED`, `REPORT_EXPORTED`, …).

**Idempotency:** Mutating financial POSTs accept `Idempotency-Key`. Angular `ApiService` sends UUID keys for create/approve/void, bank import/confirm, suggestion accept, period close ([`docs/SECURITY.md`](SECURITY.md)).

**Currency/locale defaults:** Firm default `LKR` / `Asia/Colombo`; no FX conversion in V1.

---

## 8. Areas of complexity or risk

| Area | Why risky | Careful of |
|------|-----------|------------|
| **Tenancy leaks** | Every query must filter `firmId` + client access | Study `TenantIsolationIntegrationTest`; never skip firm scope |
| **Receipt vs Document naming** | UI/API say document; DB is `receipts` | Do not create a second Document table |
| **Store-then-DB upload** | Blob before DB row; crash can orphan files | Cleanup-on-failure is best-effort ([ARCHITECTURE_INTERVIEW_ANSWERS.md](ARCHITECTURE_INTERVIEW_ANSWERS.md)) |
| **AI async + quotas** | Upload succeeds even if AI fails | Retry processing; EXTRACTED ≠ approved |
| **Event outbox unused** | `EventOutbox` / `OutboxService` without poller | Today events are Spring in-process only |
| **API vs UI gap** | Many endpoints lack SPA screens | See §9 “API-only” notes |
| **JWT residual validity** | Access tokens valid until expiry after logout | Short TTL + refresh revoke; password change revokes refresh |
| **Upload size triad** | App max, Spring multipart, Nginx body size | Raising one alone still rejects large files |
| **No antivirus / PDF rasterization** | Malicious files; AI won’t OCR PDF pages in V1 | MIME sniff + size + auth only |
| **Manual SaaS billing** | Operator-driven plan changes | `SubscriptionAccessService.assertCanWrite` blocks suspended firms |
| **Optimistic concurrency** | Approve/close races → 409 | UI must refresh and retry |
| **Unscoped document requests** | No `periodId` blocks **every** period for that client | Assign period when creating requests |
| **Not double-entry** | Income + expense + categories only | No Balance Sheet / Trial Balance / IFRS cash flow |

---

## 9. Complete user-facing action inventory

Routes are under `/api/v1`. Format:

```
[Action name]
[Trigger] → [HTTP method + route] → [Handler] → [Service] → [Models]
→ [Side effects] → [Response/end state]
```

### Public / Auth

**Login**  
login.page → POST /auth/login → LoginController → AuthenticationService+JwtService+SessionService → User+RefreshToken+AuditLog → rate-limit + LOGIN audit → LoginResponse (JWT+refresh)

**Register firm**  
register.page → POST /auth/register → RegistrationController → RegistrationService+FirmService → Firm+FirmSubscription+User(ADMIN)+Role → FIRM_REGISTERED audit → 201 RegisterResponse (**no JWT**) → sign-in required

**Refresh session**  
AuthService interceptor → POST /auth/refresh → LoginController → SessionService.refresh → RefreshToken rotated → LoginResponse

**Logout**  
shell/AuthService → POST /auth/logout → LoginController → SessionService.logout → RefreshToken revoked → 204

**Forgot password**  
API only (interceptor allowlist; no SPA page) → POST /auth/forgot-password → LoginController → SessionService.requestPasswordReset → PasswordResetToken → email if user exists → void

**Reset password**  
API only → POST /auth/reset-password → LoginController → SessionService.resetPassword → User.passwordHash+token used+all RefreshTokens deleted → void

**Get my profile**  
profile.page / shell → GET /auth/me → AuthController → UserService.getProfile → User+UserClientAccess → UserProfileResponse

**Health liveness**  
probes → GET /health → HealthController → — → `{status:UP}`

**Health readiness**  
probes → GET /health/ready → HealthController → DataSource ping → 200 READY / 503

### Self-service (any authenticated)

**Change password**  
profile.page → POST /users/me/password → UserController → UserService → User.passwordHash; revoke sessions → 204

**List notifications**  
notifications.page / bell → GET /notifications → NotificationController → NotificationService → Notification → page

**Unread count**  
bell → GET /notifications/unread-count → NotificationController → NotificationService → count

**Mark notification read**  
notifications.page → POST /notifications/{id}/read → NotificationController → NotificationService → Notification.read → updated

**Mark all notifications read**  
notifications.page → POST /notifications/read-all → NotificationController → NotificationService → bulk read

**Get/update notification preferences**  
API only → GET|PUT /notifications/preferences → NotificationController → NotificationPreferenceService → NotificationPreference → persisted flags

### Firm ADMIN — users

**List users**  
users.page → GET /users → UserController → UserService → User+UserClientAccess → PageResponse

**Get user**  
API (self or admin) → GET /users/{userId} → UserController → UserService → User → UserResponse

**Create user**  
users.page → POST /users → UserController → UserService → User+UserClientAccess; quota; audit; hash password → 201

**Update user**  
API only → PUT /users/{userId} → UserController → UserService → User; audit → UserResponse

**Activate / deactivate user**  
users.page → POST /users/{id}/activate|deactivate → UserController → UserService → User.active; deactivate revokes sessions; audit → UserResponse

**Replace client access**  
API only → PUT /users/{userId}/client-access → UserController → UserService → replace UserClientAccess; audit → List

### Firm ADMIN — clients & categories

**List / get clients**  
clients.page / many pages → GET /clients, GET /clients/{id} → ClientController → ClientService → assignment-scoped Client → page/DTO

**Create client**  
clients.page → POST /clients → ClientController → ClientService → Client; quota; audit → 201

**Update client**  
API only → PUT /clients/{id} → ClientController → ClientService → Client; audit → ClientResponse

**Assign primary accountant**  
API only → PUT /clients/{id}/primary-accountant → ClientController → ClientService → Client.primaryAccountant; audit → ClientResponse

**Activate / deactivate client**  
clients.page → POST /clients/{id}/activate|deactivate → ClientController → ClientService → Client.active; audit → ClientResponse

**List / get / create categories; activate / deactivate**  
categories.page (list/create/toggle) → GET|POST /categories, GET /categories/{id}, POST .../activate|deactivate → CategoryController → CategoryService → Category; audit → DTOs

**Update category**  
API only → PUT /categories/{id} → CategoryController → CategoryService → Category; audit → CategoryResponse

### Firm ADMIN — firm & subscription

**Get firm settings**  
firm.page → GET /firm or /settings/firm → FirmController → FirmJpaRepository → Firm(+subscription) → FirmSettingsResponse

**Update firm settings**  
firm.page → PUT /settings/firm → FirmController → inline + AuditLogger → Firm; FIRM_SETTINGS_UPDATED → FirmSettingsResponse

**Subscription summary / usage**  
subscription.page / shell badge → GET /subscription, GET /subscription/usage → SubscriptionController → SubscriptionService+UsageService → FirmSubscription → views

**Request plan upgrade**  
subscription.page → POST /subscription/upgrade-request → SubscriptionController → PlanChangeRequest repo → PlanChangeRequest; PLAN_CHANGE_REQUESTED audit → PlanChangeRequestView

**AI metrics**  
firm.page → GET /ai/metrics → AiMetricsController → AiExtractionFacade → usage aggregates → AiUsageMetrics

### Documents

**Firm inbox**  
documents.page → GET /documents → FirmDocumentController → DocumentService.listInbox → Receipt → page

**Upload document**  
documents.page / owner.page → POST /clients/{clientId}/documents → DocumentController → DocumentService.upload → Receipt+storage+AiMetadata; DocumentUploadedEvent; quota; audit → 201 (+ async AI/notify)

**List / get / download document**  
documents / review → GET .../documents, GET .../{id}, GET .../{id}/content → DocumentController → DocumentService → Receipt+blob → JSON/binary

**Reject document**  
document-review → POST .../{id}/reject → DocumentController → DocumentService.reject → Receipt REJECTED; audit → DocumentResponse

**Mark duplicate**  
document-review / API → POST .../{id}/mark-duplicate → DocumentController → DocumentService → Receipt; audit → DocumentResponse

**Create transaction from document**  
document-review → POST .../{id}/transactions → DocumentReviewService.createFromDocument → DRAFT Expense|Income + link; source MANUAL → DocumentResponse

**Unlink document**  
API / review flows → POST .../{id}/unlink → DocumentController → DocumentService → clear links; audit → DocumentResponse

**Delete document**  
document-review → DELETE .../{id} → DocumentController → DocumentService → Receipt(+storage cleanup); audit → 204

**Retry AI processing**  
document-review → POST .../{id}/retry-processing → DocumentController → DocumentService / AI facade → re-queue processing → DocumentResponse

**Reject AI suggestion**  
document-review → POST .../{id}/review/reject-suggestion → DocumentReviewService.rejectSuggestion → AI review REJECTED; document kept → DocumentResponse

**Accept AI suggestion**  
document-review → POST .../{id}/review/accept → DocumentReviewService.accept → DRAFT tx source=AI + LINKED; idempotency → DocumentResponse

**Modify AI suggestion**  
document-review → POST .../{id}/review/modify → DocumentReviewService.modify → DRAFT tx MODIFIED → DocumentResponse

### Document requests

**List / create document requests**  
period-detail / owner → GET|POST /clients/{id}/document-requests → DocumentRequestController → DocumentRequestService → DocumentRequest; notify; audit → DTOs

**Remind**  
API only → POST .../document-requests/{id}/remind → DocumentRequestService → bump reminder; email/in-app → DTO

**Attach existing document**  
API only → POST .../{id}/attach → DocumentRequestService → status UPLOADED → DTO

**Upload against request**  
owner.page → POST .../{id}/upload multipart → DocumentRequestService → creates Receipt + UPLOADED (not COMPLETED) → DTO

**Complete / cancel request**  
period-detail → POST .../{id}/complete|cancel → DocumentRequestService → COMPLETED|CANCELLED; audit → DTO

**Document request templates**  
API only → GET /document-request-templates → DocumentRequestTemplateController → static list (no DB) → template DTOs

### Expenses & Income (symmetric)

Base: `/clients/{clientId}/expenses` and `/clients/{clientId}/income`

**List / get**  
expenses.page / income.page → GET, GET /{id} → ExpenseController|IncomeController → ExpenseService|IncomeService → ledger rows (auditors finalized-only) → page/DTO

**Create**  
expenses/income pages → POST → *Service.create → DRAFT; period-open check; audit; idempotency → DTO

**Update / delete draft**  
API only → PUT /{id}, DELETE /{id} → *Service → draft-only; period-open; audit → DTO / 204

**Approve**  
expenses/income pages → POST /{id}/approve → *Service.approve → APPROVED; audit; idempotency → DTO

**Void**  
expenses/income pages → POST /{id}/void → *Service.void* → VOIDED + reason; audit; idempotency → DTO

**Link document**  
expenses/income / review → POST /{id}/documents → DocumentService link → join table; audit → DTO

**Unlink document from transaction**  
API only → DELETE /{id}/documents/{documentId} → DocumentService → unlink; audit → DTO

### Banking

Base: `/clients/{clientId}/bank`

**List / create bank accounts**  
banking.page → GET|POST /accounts → BankController → BankAccountService → BankAccount; audit → DTOs

**Update bank account**  
API only → PUT /accounts/{accountId} → BankAccountService → BankAccount; audit → DTO

**Preview CSV import**  
banking.page → POST /imports/preview → BankReconciliationService.previewImport → parse only → preview DTO

**Import CSV**  
banking.page → POST /imports → BankReconciliationService.importCsv → BankImport+BankTransaction+suggestions; storage; audit; idempotency → BankImportResponse

**List imports / transactions / dashboard / recon summary**  
banking.page (most) → GET /imports, /transactions, /dashboard, /reconciliation/summary → BankReconciliationService → read models → DTOs

**Confirm / reject / unmatch / ignore match**  
banking.page → POST .../transactions/{id}/confirm|reject|unmatch|ignore → BankReconciliationService → ReconciliationMatch+BankTransaction; period-open; audit; idempotency on confirm → DTO

**Regenerate suggestions**  
API only → POST .../suggestions/regenerate → BankReconciliationService → refresh suggestions → DTO

**Create expense / income from bank**  
banking.page → POST .../create-expense|create-income → BankReconciliationService → DRAFT ledger + PENDING_APPROVAL bank status → DTO

**Request document from bank**  
banking.page → POST .../request-document → BankReconciliationService → DocumentRequest; audit → DTO

### Period close

**Close work queue**  
close.page → GET /close/work-queue → CloseWorkQueueController → PeriodCloseService.workQueue → derived readiness → page

**List periods / get or create month**  
close.page → GET|POST /clients/{id}/periods → PeriodController → PeriodCloseService → AccountingPeriod; audit on create → PeriodResponse

**Get period / readiness**  
period-detail → GET .../periods/{id}, GET .../readiness → PeriodCloseService → evaluate CloseChecks; may notify PERIOD_READY → DTOs

**Start review**  
period-detail → POST .../review → PeriodCloseService.startReview → IN_REVIEW; audit → PeriodResponse

**Close period**  
period-detail → POST .../close → PeriodCloseService.close → CLOSED if ready else PERIOD_NOT_READY_TO_CLOSE; notify; audit; idempotency → PeriodResponse

**Reopen period**  
period-detail (ADMIN) → POST .../reopen → PeriodCloseService.reopen → REOPENED + reason; audit → PeriodResponse

### Reporting

Roles: ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER (UPLOAD_ONLY blocked)

**P&L / comparison / income / expenses / trends / dashboard**  
reports pages → GET .../reports/profit-and-loss(|/comparison), income, expenses, trends, dashboard → ReportingController → ReportingFacade → APPROVED aggregates → JSON

**Cash movement / top categories / document support / status summary**  
reports.page (status-summary wired; others API / partial) → matching GET paths → ReportingFacade → summaries → JSON

**Export P&L / income / expenses**  
reports / period-detail → GET .../export?format=csv|xlsx → ReportExportService → file + REPORT_EXPORTED audit → download

**Practice dashboard**  
dashboard.page → GET /reports/practice → PracticeReportingController → firm workload (no cross-client $) → PracticeDashboard

### Practice work & activity

**Work summary / my work / portfolio**  
work.page / dashboard → GET /work/summary|my|portfolio → WorkController → PracticeWorkQueueService → derived work items → DTOs

**Staff workload**  
API only → GET /work/staff-workload → WorkController → PracticeWorkQueueService → per-accountant counts → DTO

**Activity feed**  
API only → GET /activity → ActivityFeedController → AuditLog read model → page

**Audit log**  
audit.page → GET /audit → AuditController → AuditLog (ADMIN, AUDITOR) → page

### Platform administration (SaaS)

Guarded by `@platformAdminService.isCurrentUserPlatformAdmin()` (except `/me`).

**Am I platform admin?**  
platform.guard / AuthService → GET /platform/me → PlatformController → PlatformAdminService → grant flag → `{platformAdmin}`

**Platform metrics / list firms / firm detail**  
platform.page / platform-firm.page → GET /platform/metrics, /firms, /firms/{id} → PlatformController → Firm+FirmSubscription+UsageService → views

**Change plan / status / extend trial**  
platform-firm.page → PUT .../subscription/plan|status, POST .../extend-trial → SubscriptionService → FirmSubscription; audit → FirmDetail

**Grant / revoke platform admin**  
API only (+ bootstrap runner) → POST /platform/admins/{userId}/grant|revoke → PlatformAdminService → PlatformAdminGrant → void/DTO

**Bootstrap:** `PlatformAdminBootstrapRunner` on startup if `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` / `app.platform.bootstrap-admin-email` set.

### Background (system)

**Subscription maintenance**  
cron `SubscriptionMaintenanceScheduler` → FirmSubscription status/period; notifications (no HTTP)

**Overdue document reminders**  
cron `OverdueDocumentRequestScheduler` → notifications/email for OPEN overdue requests

**Async AI extraction**  
`DocumentUploadedListener` → `DocumentAiProcessor` → Receipt AI fields

**Workflow notifications**  
`WorkflowNotificationListener` → Notification(+email intent)

---

## Angular routes (screens)

From `frontend/src/app/app.routes.ts`:

| Path | Guard | Page |
|------|-------|------|
| `/login`, `/register` | guest | auth |
| `/app/dashboard` | auth + ledger | dashboard |
| `/app/work` | ADMIN, ACCOUNTANT | work |
| `/app/notifications` | auth | notifications |
| `/app/clients` | ADMIN, ACCOUNTANT, AUDITOR | clients |
| `/app/documents`, `.../:clientId/:documentId` | auth | documents / review |
| `/app/expenses`, `/income` | ledger | transactions |
| `/app/banking`, `/close`, `/close/:c/:p` | staff + ledger | banking / close |
| `/app/reports*` | ledger | reports |
| `/app/users`, `/categories`, `/firm`, `/subscription` | ADMIN | admin |
| `/app/audit` | ADMIN, AUDITOR | audit |
| `/app/owner` | ownerGuard | BUSINESS_OWNER / upload-only |
| `/app/profile` | auth | profile |
| `/platform`, `/platform/firms/:id` | platformGuard | SaaS console |

### API-only surfaces (no dedicated SPA UI)

Confirmed by comparing controllers to `frontend/src` call sites:

- Forgot / reset password pages
- User update + replace client-access
- Client update + primary-accountant assignment
- Category update (create/toggle only in UI)
- Notification preferences
- Document request remind / attach / templates
- Expense/income update, delete, unlink-document
- Bank account update; regenerate suggestions
- Staff workload; activity feed
- Platform admin grant/revoke (bootstrap + API)
- Some report endpoints (cash-movement, top-categories) without dedicated pages

Use Postman (`postman/`) or curl for those; do not assume missing UI means missing capability.

---

## Suggested reading order

1. This file + [`README.md`](../README.md) + [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md)
2. [`SECURITY.md`](SECURITY.md), [`AI.md`](AI.md), [`CLOSE.md`](CLOSE.md), [`BANK_RECONCILIATION.md`](BANK_RECONCILIATION.md), [`SAAS_SUBSCRIPTIONS.md`](SAAS_SUBSCRIPTIONS.md)
3. `TenantAwareEntity` + `JwtAuthenticationFilter` + `GlobalExceptionHandler`
4. `DocumentService` + `DocumentReviewService` + `TransactionStatusRules` + `ExpenseService.approve`
5. `BankReconciliationService` + `PeriodCloseService`
6. `SubscriptionAccessService` + `PlatformController`
7. Frontend `app.routes.ts` + `auth.guard.ts` + one feature page end-to-end
