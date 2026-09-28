# CV Project Analysis — Finance Platform

Evidence-based analysis derived from repository inspection (README, docs, source, migrations, CI). Generated 2026-09-27.

**Authorship (confirmed by project owner):** This is a **sole-authored, end-to-end** project — architecture, backend, frontend, database schema, security, CI/CD, E2E tests, and documentation were built by one developer. CV language below uses **I / sole developer** where ownership matters; technical claims remain tied to repository evidence.

---

## 1. PROJECT OVERVIEW

| Field | Value |
| --- | --- |
| **Project name** | Finance Platform (finance-ai-automation-platform) |
| **Project type** | Full-stack B2B SaaS — multi-tenant bookkeeping / practice operations |
| **Primary purpose** | Accountant-first **Document-to-Close** workspace for bookkeeping firms managing many SME clients |
| **Problem being solved** | Fragmented evidence collection, manual data entry, weak close controls, and lack of a single workflow from document upload through ledger approval, bank reconciliation, and period close |
| **Likely target users** | Accounting firms (admins, accountants, auditors), SME **business owners** (upload/respond), platform operators (SaaS admin) |
| **Current maturity** | **Pilot-ready** (extensive feature set, Flyway schema V1–V40, Docker Compose, GitHub Actions including Postgres integration tests and Playwright E2E; internal audit notes remaining P0-adjacent items before commercial “flagship”) |
| **Ownership** | **Sole developer** — full stack and platform (no separate team credited in this analysis) |

### Technical explanation (3–5 sentences)

The system is a **modular monolith**: an Angular 20 SPA talks to a Spring Boot 4.x / Java 17 REST API at `/api/v1`, backed by **PostgreSQL 16** with **Flyway** migrations. Each **firm** is a tenant; users authenticate with **JWT access tokens** (in-memory in the browser) and **HttpOnly refresh cookies**, with authorization enforced via Spring Security, `@PreAuthorize`, and a **`ClientAccessService`** matrix (role + per-client access type). Core workflows cover document upload and optional **AI extraction** (outbox-driven async processing), human review, draft/approve/void **income and expense** ledger entries, **CSV bank import and reconciliation**, evidence-driven **month-end close**, **P&L reporting and exports**, practice **work queues**, **accounts receivable** (invoices/payments), subscriptions, and structured **audit logging**. File evidence is stored locally or on **S3-compatible** object storage; optional **OpenAI-compatible** LLM and **SMTP** email integrate when configured.

---

## 2. TECHNOLOGY STACK

*(Only technologies found in the repository.)*

| Category | Technologies |
| --- | --- |
| **Frontend** | Angular 20, Angular Material, Angular CDK, RxJS, `@ngx-translate`, AG Grid, Chart.js / ng2-charts, ngx-extended-pdf-viewer, TypeScript ~5.8, Vitest (unit), Prettier |
| **Backend** | Spring Boot 4.1, Spring Web MVC, Spring Security, Spring Data JPA, Spring Validation, Spring Actuator, SpringDoc OpenAPI 3.1, Gradle (multi-module), Lombok |
| **Languages** | Java 17, TypeScript, SQL (Flyway), Python (demo seed script for E2E) |
| **Frameworks** | Spring Boot (backend); Angular (frontend); Playwright (E2E) |
| **Database** | PostgreSQL 16 (runtime); H2 (some test runtime) |
| **ORM / data access** | Spring Data JPA / Hibernate; custom SQL repositories (e.g. reporting, practice work queries) |
| **Authentication** | JWT (HMAC), server-side hashed refresh tokens, BCrypt password hashing (strength 12), email verification tokens, password reset tokens, session revocation on password change / refresh reuse |
| **Authorization** | Role-based (`ADMIN`, `ACCOUNTANT`, `AUDITOR`, `BUSINESS_OWNER`); client access types (`FULL`, `READ_ONLY`, `UPLOAD_ONLY`); platform admin grants table |
| **Cloud** | Documented deployment on **Oracle Cloud (OCI)** and generic VPS; **AWS S3-compatible** storage optional — no AWS SDK required for core app beyond S3 client usage in storage layer |
| **Containers** | Docker (backend `Dockerfile`), Docker Compose (`docker-compose.yml`, `docker-compose.prod.yml`) |
| **CI/CD** | GitHub Actions: backend (Gradle test + Testcontainers Postgres + bootJar), frontend (npm build), E2E (Playwright + Postgres service) |
| **Testing** | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), JaCoCo (platform-app), Vitest (1 frontend spec), Playwright (8 E2E spec files) |
| **Monitoring / logging** | Spring Actuator, Micrometer Prometheus registry, health (`/api/v1/health`, `/api/v1/health/ready`), `TenantMdcFilter`, `SecurityEventLogger`, structured audit log |
| **Messaging / queues** | **Transactional outbox** (`event_outbox`, `OutboxWorker`) — not Kafka/RabbitMQ |
| **Caching** | Optional **Redis** for auth rate-limit store (`RedisRateLimitStore`); no general application cache called out as primary |
| **External APIs** | OpenAI-compatible chat/vision (optional AI extraction); Jakarta Mail SMTP (optional notifications) |
| **Other** | Flyway; Apache POI (XLSX exports); Nginx configs in `deploy/nginx/`; idempotency middleware for financial POSTs |

---

## 3. ARCHITECTURE

### Identified patterns (code-supported)

- **Modular monolith** — six Gradle modules compose into one `platform-app` JAR/process (`README.md`, `backend/settings.gradle` pattern).
- **Layered architecture** — Controllers → application services → JPA repositories / query repositories (`.cursor/rules/backend.mdc`, consistent package layout).
- **Clean / hexagonal influences** — domain entities in `domain/model`, infrastructure in `infrastructure/persistence`, facades between modules (`UserFacade`, `ReportingFacade`), without full strict ports/adapters everywhere.

**Not present as primary architecture:** microservices, event-driven mesh (beyond in-process outbox + domain events), separate API gateway.

### Major modules

| Module | Responsibility |
| --- | --- |
| `platform-core` | Tenant context, audit, exceptions, idempotency filter, outbox worker, file storage abstraction, notifications |
| `module-auth` | Users, registration, JWT filter, refresh sessions, rate limiting, lockout |
| `module-finance` | Clients, categories, documents (`Receipt`), ledger, bank, close, practice ops, AR, chase, subscriptions |
| `module-ai` | Document extraction and accounting suggestions (optional providers) |
| `module-reporting` | Read-only P&L, dashboards, SQL aggregations |
| `platform-app` | REST controllers, Flyway migrations, health, platform admin HTTP API |

### Boundaries

- HTTP API only at `platform-app` controllers; finance/auth logic stays in respective modules.
- Cross-module calls via facades and Spring events (e.g. `DocumentUploadedEvent` → AI outbox handler).
- Frontend is a separate deployable SPA (not embedded in Spring static resources).

### Request / data flow (typical)

1. Browser → Angular `ApiService` (Bearer access token; credentials for refresh).
2. `JwtAuthenticationFilter` validates JWT, **reloads user from DB**, checks `firmId` claim vs user, sets security context.
3. Controller → service → `@Transactional` persistence; tenant scoping via `firmId` on entities and query filters.
4. Mutations may write **audit_log**, enqueue **outbox** events (AI), or require **Idempotency-Key**.
5. Reads for reports use SQL aggregations on **APPROVED** ledger rows only.

### Database structure

- ~**50+ tables** from Flyway migrations (ledger, evidence, bank, close, notifications, AR, reliability, SaaS).
- Firm-scoped FKs (`firm_id`), soft-delete patterns on several entities, partial unique indexes (e.g. categories).
- Views such as `v_approved_transactions` for reporting.

### Authentication flow

1. Register firm + admin (no JWT on register — sign in separately).
2. Login → access JWT + HttpOnly `fp_refresh` cookie.
3. SPA bootstraps session via `POST /api/v1/auth/refresh`.
4. 401 → single in-flight refresh + one retry; logout clears cookie and revokes refresh row.
5. Refresh rotation; reuse of revoked refresh revokes all sessions.

### Deployment architecture (visible)

- **Model A:** same origin — Nginx serves Angular + proxies `/api` (`deploy/nginx/same-origin.conf`).
- **Model B:** separate app/API domains with CORS (`APP_CORS_ALLOWED_ORIGINS`).
- Docker Compose: Postgres + backend + frontend for local/demo; prod compose variant documented.

### Multi-tenancy

- Tenant = **firm** (`firmId` on JWT and on firm-scoped rows).
- **Defense in depth:** token claim + DB user reload + service-level client assignment checks.
- Cross-tenant ID probing returns 404/403 per convention (`docs/SECURITY.md`).

---

## 4. FEATURES IMPLEMENTED

| Feature | What it does | Technologies involved | Complexity | Evidence / location |
| --- | --- | --- | --- | --- |
| Firm registration & auth | Register firm, login, refresh, logout, forgot/reset password, email verify | JWT, BCrypt, cookies, rate limit | High | `LoginController`, `RegistrationController`, `SessionService`, `V14`, `V29` |
| Session revocation & abuse protection | Lockout, rate limits, refresh reuse detection, security version on user | Redis or in-memory rate store, integration tests | High | `LoginLockoutService`, `AuthRateLimiter`, `SessionRevocationIntegrationTest`, `V40` |
| User management | CRUD users, activate/deactivate, client access assignment, password change | RBAC, JPA | Medium | `UserController`, `UserService` |
| RBAC + client access | Role + FULL/READ_ONLY/UPLOAD_ONLY matrix for ledger, docs, reports, bank | `@PreAuthorize`, `ClientAccessService` | High | `docs/SECURITY.md`, `ClientAccessServiceAuthorizationTest` |
| Client lifecycle | Clients, primary accountant, activate/deactivate | JPA, audit | Medium | `ClientController`, `ClientService`, `V22` |
| Categories | Firm/client category packs, activate/deactivate | JPA, partial unique indexes | Medium | `CategoryController`, `V3`, `V8` |
| Document / receipt management | Multipart upload, checksum, inbox, review, link/unlink to ledger, secure download | Local/S3 storage, MIME sniffing | High | `DocumentController`, `DocumentService`, `V17` |
| AI document extraction | Async processing, suggestions, accept/modify/reject → DRAFT only | Outbox, OpenAI/mock/disabled providers | High | `module-ai`, `DocumentAiOutboxHandler`, `V19`, `docs/AI.md` |
| Expense / income ledger | CRUD, approve, void, document attachments, draft isolation for auditors | Transactions, period guards | High | `ExpenseController`, `IncomeController`, `V4` |
| Idempotency on financial POSTs | Fingerprint method+URI+body; stable client keys | Servlet filter, DB keys | High | `IdempotencyFilter`, `IdempotencyFrameworkIntegrationTest`, `V25` |
| Reporting & exports | P&L, comparisons, trends, dashboards, CSV/XLSX | SQL aggregations, Apache POI | High | `ReportingController`, `module-reporting`, `V18` |
| Accounting periods & month-end close | OPEN→IN_REVIEW→CLOSED, readiness checks, reopen | Pluggable `CloseCheck` engine | High | `PeriodController`, `PeriodCloseService`, `docs/CLOSE.md`, `V20` |
| Bank CSV import | Preview, column mapping, duplicate detection (file + row fingerprint) | CSV importer, checksums | High | `BankController`, `GenericBankStatementCsvImporter`, `BankTransactionFingerprint`, `V21`, `V31` |
| Bank reconciliation | Match suggestions, confirm/reject, create draft from bank line, invoice payment confirm | Scoring service, concurrency claims | High | `BankReconciliationService`, `BankReconciliationConcurrencyIntegrationTest`, `V38`, `V39` |
| Document requests | Staff creates requests; owners upload; reminders; period linkage | Notifications, schedulers | Medium | `DocumentRequestController`, `V12`, `V22` |
| Practice work queue | Derived work items, month-end command center, assignments | SQL read models | High | `WorkController`, `PracticeWorkQueueService`, `V22` |
| Notifications & email | In-app notifications, preferences, SMTP or log provider | Outbox/dispatcher, Jakarta Mail | Medium | `NotificationController`, `V14`, `V22` |
| Activity feed | Audit-derived activity stream | Audit log query | Low | `ActivityFeedController` |
| Audit trail | Queryable firm-scoped audit log for admins/auditors | `audit_log` table | Medium | `AuditController`, `V7` |
| Subscriptions & quotas | Plans, trial, suspend, usage, upgrade requests, pessimistic quota locks | JPA locking | Medium | `SubscriptionController`, `SubscriptionService`, `V15`, `V23` |
| Platform admin | Grant/revoke platform admins, firm/subscription ops (no client ledger access) | Separate API namespace | Medium | `PlatformController`, `V24` |
| Accounts receivable | Customers, sales invoices (issue/void/send/PDF), payments, allocations, ageing | Invoicing services, HTML/PDF render | High | `SalesInvoiceController`, `ArPaymentController`, `V33`–`V36` |
| Client chase automation | Policies, runs, suppress/resume, scheduled execution | Scheduler | Medium | `ClientChaseController`, `ClientChaseScheduler`, `V33` |
| Monthly evidence checklist | Checklist items, generate document requests | Close/evidence | Medium | `ClientMonthlyEvidenceController`, `V30` |
| Firm documents & owner portal | Firm-level docs; simplified owner UX | Angular guards | Medium | `FirmDocumentController`, `owner.page.ts` |
| Health & readiness | Liveness and DB readiness probes | Actuator/custom controllers | Low | `HealthController` |
| OpenAPI | API documentation UI | SpringDoc | Low | `springdoc-openapi` dependency |
| E2E demo seed | Python script seeds demo data for CI | Python | Low | `demo/seed_demo.py`, `e2e.yml` |
| Integration placeholders | Schema for automation rules, external integrations — **no Java app layer** | SQL only | N/A | `V33` (`integration_connections`, `automation_rules`) |

---

## 5. BACKEND ENGINEERING

| Area | Evidence |
| --- | --- |
| **REST API design** | Resource-oriented controllers under `/api/v1`; OpenAPI; consistent error codes in `ErrorCodes` |
| **Controllers / endpoints** | **34** controller classes; **~180** `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` handler methods (counted in `platform-app/src/main/java/.../controller`) |
| **Services / business logic** | Rich application layer in `module-finance` (e.g. `BankReconciliationService`, `PeriodCloseService`, `DocumentService`) |
| **Validation** | Jakarta Bean Validation on DTOs; domain guards (period closed, client access) |
| **Exception handling** | `GlobalExceptionHandler` in `platform-core` |
| **Database transactions** | Widespread `@Transactional` on services (40+ usages in module-finance alone) |
| **Pagination / filtering / sorting** | Page requests on lists (documents, audit, notifications, work queue); capped page sizes documented |
| **Concurrency handling** | Reconciliation bank claims, subscription quota tests, import batch writers |
| **Scheduled / background** | `OutboxWorker`, overdue document request scheduler, subscription maintenance, client chase scheduler, idempotency cleanup |
| **Idempotency** | `IdempotencyFilter` + persisted keys |
| **Caching** | Not a core pattern; Redis optional for rate limits only |
| **Rate limiting** | Auth endpoints via pluggable store |
| **File handling** | Multipart uploads, storage abstraction, streaming download through API |
| **API integrations** | OpenAI-compatible HTTP client for AI; SMTP for email |

---

## 6. DATABASE ENGINEERING

| Topic | Details |
| --- | --- |
| **Database** | PostgreSQL 16 |
| **Important tables / entities** | **~50+** tables from migrations; **~45** JPA `@Entity` classes |
| **Relationships** | Firm → clients → users/access; ledger ↔ receipt link tables; bank imports → transactions → reconciliation matches; AR invoices → lines, payments → allocations |
| **Constraints** | FKs with RESTRICT, unique (firm, name) on clients, role codes, invoice number sequences |
| **Indexes** | Dedicated migration files (`V5`, `V16`, `V17`, partial uniques on categories) |
| **Migrations** | **40** Flyway version files (`V1`–`V40`) |
| **Transactions** | Service-layer `@Transactional`; outbox pattern for reliable async side effects |
| **Audit / history** | `audit_log`; AI `document_processing_attempts`; soft-delete columns where used |
| **Multi-tenant separation** | `firm_id` on tenant data; tests: `TenantIsolationIntegrationTest`, `TenantIsolationMatrixIntegrationTest` |
| **Complex models** | Reconciliation match groups + bank ledger generation tracking; AR payment allocations with reversal; idempotency + outbox reliability tables |

**Strong design examples**

- **Idempotency keys** + request body fingerprint for exactly-once HTTP semantics on critical POSTs (`V25`).
- **Transactional outbox** for AI processing decoupling (`V32`, `V25`).
- **Partial unique indexes** for category naming per scope (`V8`).
- **Bank import duplicate protection** at file checksum and row fingerprint level (`V31`, `BankTransactionFingerprint`).

---

## 7. SECURITY

| Feature | Implementation (where / how) |
| --- | --- |
| **JWT** | `JwtTokenProvider`, `JwtAuthenticationFilter`; HMAC secret via `APP_JWT_SECRET`; claims include `firmId`; `ProductionJwtSecretValidator` in prod |
| **OAuth/OIDC** | **Not implemented** — custom JWT auth only |
| **Sessions** | Stateless access; **refresh tokens** hashed in `refresh_tokens` (`SessionService`, `V14`) |
| **Password hashing** | `BCryptPasswordEncoder(12)` in `AuthModuleConfig` |
| **RBAC / permissions** | `Role.RoleCode` enum; `@PreAuthorize` on controllers; `ClientAccessService` in services (`docs/SECURITY.md`) |
| **CSRF** | Disabled for Bearer API; **Origin validation** on cookie-auth POSTs via `AuthRefreshCookieSupport` when CORS origins configured |
| **CORS** | `CorsConfig` — configurable `app.cors.allowed-origins` for `/api/**` |
| **Rate limiting** | `AuthRateLimiter` + `InMemoryRateLimitStore` / `RedisRateLimitStore`; HTTP 429 + Retry-After |
| **Lockout** | `LoginLockoutService` (unit + integration tests) |
| **Input validation** | Spring Validation on request DTOs; domain validation for AI output |
| **Secrets management** | `.env.example` placeholders; no secrets in repo; env vars for JWT, DB, S3, AI keys |
| **Tenant isolation** | DB reload in JWT filter; firm-scoped queries; extensive integration tests |
| **Secure headers** | `AuthSecurityConfig` (HSTS, frame deny, referrer policy); additional headers documented for Nginx in prod |
| **Token refresh / revocation** | Rotating refresh; reuse revokes all sessions; password change / deactivation revokes sessions (`docs/SECURITY.md`, `V40` user security version) |
| **Audit logging** | `AuditLogger` → `audit_log`; security events via `SecurityEventLogger` |
| **File upload security** | MIME sniffing, size limits, server-generated storage keys — `FileUploadSecurityIntegrationTest` |
| **AI trust boundary** | AI creates **DRAFT** only; human approval required |

---

## 8. TESTING AND QUALITY

| Type | Evidence |
| --- | --- |
| **Unit tests** | Module tests (auth rate limit, lockout, HTTP support, bank fingerprint, reconciliation progress, client access authorization) |
| **Integration tests** | **~28** distinct `*IntegrationTest.java` / Postgres-tagged tests in `platform-app` (tenant isolation matrix, golden path, bank concurrency, idempotency, JWT, session revocation, Flyway fresh DB) |
| **End-to-end tests** | Playwright: `e2e/tests/` — auth, golden-path, tenant-workflows, invoice-to-cash, critical-paths, responsive-layout, client-demo-rehearsal |
| **API tests** | Integration tests hit REST via `MockMvc` / full Spring context (`BaseWebIntegrationTest`) |
| **Test frameworks** | JUnit 5, Spring Boot Test, Testcontainers PostgreSQL |
| **Linting / formatting** | Prettier on frontend (`format:check` in package.json) |
| **Static analysis** | No Checkstyle/SpotBugs config found in quick scan |
| **Code quality tools** | JaCoCo configured on `platform-app` (reports generated when tests run) |
| **Coverage** | **Coverage not verified** (no committed coverage report in repo; JaCoCo runs on `:platform-app:test` but percentage not stored here) |

---

## 9. DEVOPS / CLOUD

| Item | Present? | Notes |
| --- | --- | --- |
| Docker | Yes | Backend Dockerfile; Compose dev + prod |
| Docker Compose | Yes | Postgres + backend + frontend |
| Kubernetes | No | Not in repo |
| Terraform / Bicep | No | Not in repo |
| AWS | Partial | S3-compatible storage config only |
| Azure / GCP | No | Not in repo |
| Oracle Cloud | Documented | `docs/ORACLE_CLOUD_HOSTING.md` |
| GitHub Actions | Yes | `backend.yml`, `frontend.yml`, `e2e.yml` |
| Deployment scripts | Docs + Nginx configs | `docs/DEPLOYMENT.md`, `docs/VPS_HOSTING.md`, `deploy/nginx/` |
| Reverse proxy | Nginx configs | Same-origin and split-domain |
| SSL/TLS | Documented at proxy | HSTS in Spring + proxy guidance |
| Environment configuration | `.env.example`, `application.yml`, profile-specific YAML |
| Secrets | Env-based | Documented in README / SECURITY |
| DB backup / recovery | Documented | `docs/reports/BACKUP_RECOVERY_IMPLEMENTATION.md`, DR runbook |
| Monitoring / alerts | Actuator + Prometheus dependency | No full alerting stack in repo |

**Deployment flow (visible)**

1. CI: unit tests → Flyway on clean Postgres (Testcontainers) → Postgres integration suite → tenant security suite → bootJar (backend).
2. E2E on `main`/PR: build JAR, start Postgres service, run backend + `ng serve`, seed demo, Playwright.
3. Production: build images or JAR + static Angular; Nginx terminates TLS and routes; env vars for DB, JWT, CORS, storage, AI, mail.

---

## 10. COMPLEX ENGINEERING PROBLEMS

### 1. Multi-tenant authorization matrix

- **Problem:** Firms with many clients; mixed roles and per-client access; auditors must not see drafts; owners upload-only without ledger write.
- **Implementation:** JWT + DB user reload; `ClientAccessService` methods; `@PreAuthorize`; frontend guards mirroring rules.
- **Why challenging:** Consistent 404/403 semantics and no leakage across firm or client boundaries.
- **Files:** `JwtAuthenticationFilter`, `ClientAccessService`, `TenantIsolationMatrixIntegrationTest`, `docs/SECURITY.md`
- **Skills:** Security architecture, domain modeling, integration testing

### 2. Idempotent financial mutations

- **Problem:** Double-submit on approve, bank confirm, period close must not double-post.
- **Implementation:** `IdempotencyFilter` fingerprints requests; DB-backed keys; Angular stable in-flight keys.
- **Why challenging:** HTTP retries, concurrent clients, body hashing with repeatable request wrapper.
- **Files:** `IdempotencyService`, `IdempotencyFilter`, `IdempotencyFrameworkIntegrationTest`
- **Skills:** Distributed systems basics, servlet filters, API design

### 3. Bank import duplicate protection & reconciliation concurrency

- **Problem:** Re-import same CSV; concurrent accountants confirming same bank line.
- **Implementation:** File checksum + `BankTransactionFingerprint`; claim columns/migrations for active reconciliation claims.
- **Why challenging:** Correctness under concurrency without losing legitimate duplicates.
- **Files:** `BankTransactionFingerprint`, `BankImportDuplicateProtectionIntegrationTest`, `BankReconciliationConcurrencyIntegrationTest`, `V38`–`V39`
- **Skills:** Data integrity, PostgreSQL constraints, concurrency

### 4. Evidence-driven month-end close engine

- **Problem:** Close readiness depends on drafts, documents, requests, bank state — extensible rules.
- **Implementation:** Pluggable `CloseCheck` beans; `PeriodCloseService`; closed-period guards across ledger/bank/AI.
- **Why challenging:** Composable rules, warnings vs blockers, post-close evidence policies.
- **Files:** `PeriodCloseService`, `CloseReadinessService`, `ClosedPeriodIntegrityIntegrationTest`, `docs/CLOSE.md`
- **Skills:** Workflow engines, accounting domain

### 5. Transactional outbox for AI processing

- **Problem:** Document upload must commit before async AI; avoid lost events on crash.
- **Implementation:** `event_outbox`, `OutboxWorker`, `DocumentAiOutboxHandler`.
- **Why challenging:** At-least-once processing, status lifecycle on receipts, firm AI toggle.
- **Files:** `OutboxService`, `module-ai`, `V32`
- **Skills:** Reliability patterns, event-driven design within monolith

### 6. Refresh token rotation and session hijack detection

- **Problem:** Stolen refresh tokens; password change must invalidate sessions.
- **Implementation:** Hashed refresh storage, rotation, reuse → revoke all; security version on user (`V40`).
- **Why challenging:** Cookie + SPA semantics, multi-device logout story.
- **Files:** `SessionService`, `SessionRevocationIntegrationTest`, `AuthRefreshCookieSupport`
- **Skills:** Auth systems, threat modeling

### 7. Deterministic bank reconciliation scoring

- **Problem:** Suggest ledger matches for bank lines without ML black box.
- **Implementation:** `ReconciliationSuggestionService` with documented scoring and direction rules.
- **Why challenging:** Explainability, debit/credit direction, period boundaries.
- **Files:** `BankReconciliationService`, `docs/BANK_RECONCILIATION.md`
- **Skills:** Domain algorithms, UX for accountants

### 8. SQL-first financial reporting

- **Problem:** Accurate P&L and exports on approved data only, per client, at scale.
- **Implementation:** `ReportingQueryRepository`, approved view, POI XLSX with numeric cells.
- **Why challenging:** Avoid double-counting categories; empty vs error semantics.
- **Files:** `module-reporting`, `V18`, `ReportingController`
- **Skills:** SQL analytics, reporting APIs

### 9. Accounts receivable and bank-linked invoice settlement

- **Problem:** Invoices, payments, allocations, reversals, bank transaction confirmation paths.
- **Implementation:** AR module in finance; migrations V33–V36; integration tests for invoice-to-cash and reversals.
- **Why challenging:** Ledger-adjacent correctness and audit columns on allocations.
- **Files:** `ArPaymentService`, `InvoiceToCashIntegrationTest`, `BankInvoicePaymentReversalIntegrationTest`
- **Skills:** Financial subledger design

### 10. Practice work queue as derived read model

- **Problem:** No duplicate “task” DB; surface actionable work across modules.
- **Implementation:** `PracticeWorkQueryRepository`, command center APIs, assignment tables.
- **Why challenging:** Consistent prioritization from live accounting state.
- **Files:** `WorkController`, `MonthEndCommandCenterService`, `GoldenPathWorkflowIntegrationTest`
- **Skills:** Read models, operational UX backend

---

## 11. SOFTWARE ENGINEERING PRACTICES

| Practice | Example |
| --- | --- |
| **SOLID / SRP** | Separate modules (auth vs finance vs reporting); thin controllers |
| **Design patterns** | Outbox, facade (`UserFacade`, `ReportingFacade`), strategy (AI providers, bank importers), pluggable close checks |
| **DTOs** | Request/response records in application/dto packages |
| **Dependency injection** | Spring `@Service`, `@Configuration`, constructor injection (Lombok `@RequiredArgsConstructor`) |
| **Repository / service** | `*JpaRepository` + application services — standard Spring layering |
| **Domain modelling** | Entities with enums for status (ledger, documents, periods, bank) |
| **Modularization** | Gradle subprojects with enforced boundaries via dependencies |
| **Separation of concerns** | AI in `module-ai`; reporting read-only module |
| **Configuration management** | `application.yml`, `@ConfigurationProperties` (`AuthProperties`, `JwtProperties`) |
| **Error handling** | Central handler, typed error codes |
| **Observability** | MDC tenant filter, security event logger, actuator |
| **API versioning** | `/api/v1` prefix |

---

## 12. OWNERSHIP SIGNALS

**Confirmed:** sole developer, end-to-end. The repository supports strong **individual ownership** narrative for CV and interviews:

| Area | What you can claim |
| --- | --- |
| **Architecture** | Designed and documented the modular monolith (vertical-slice docs, security/schema guides, flagship audit) |
| **Backend** | Implemented ~180 REST handlers, domain services (finance, bank, close, AR), platform-core (outbox, idempotency, audit) |
| **Frontend** | Built Angular 20 SPA (~48 feature modules/pages), guards, practice UX (work queue, banking, close, AR, platform admin) |
| **Database** | Authored 40 Flyway migrations, indexes, reliability tables (outbox, idempotency), AR and reconciliation schema |
| **Security** | JWT + refresh rotation, tenant isolation tests, rate limiting, lockout, file upload security, RBAC matrix |
| **Deployment** | Docker Compose (dev/prod), Nginx configs, VPS and OCI hosting documentation |
| **Infrastructure** | GitHub Actions for backend (Testcontainers), frontend build, Playwright E2E pipeline |
| **Documentation** | User guide, implementation status by phase, close/bank/AI/deployment/runbooks |
| **Testing** | Integration suites (tenant matrix, golden path, bank concurrency, idempotency) + Playwright E2E |
| **Operational readiness** | Health/readiness, backup/DR docs, subscription/SaaS ops — with honest gaps noted in internal audits |

Use phrasing like: *“Designed and built a multi-tenant finance platform as sole developer.”* Avoid implying a team unless you add collaborators later.

---

## 13. METRICS WE CAN SAFELY USE

| Metric | Verified value |
| --- | --- |
| Backend Gradle modules (product) | **6** (`platform-core`, `module-auth`, `module-finance`, `module-ai`, `module-reporting`, `platform-app`) |
| REST controller classes | **34** |
| Approximate HTTP handler methods | **~180** |
| Flyway migrations | **40** (`V1`–`V40`) |
| Approximate DB tables (CREATE TABLE in migrations) | **~50+** |
| JPA entity classes | **~45** |
| User roles | **4** (`ADMIN`, `ACCOUNTANT`, `AUDITOR`, `BUSINESS_OWNER`) |
| Client access types | **3** (`FULL`, `READ_ONLY`, `UPLOAD_ONLY`) |
| Backend `*Test.java` files | **~33–34** (including module unit tests) |
| Postgres/integration test classes (platform-app) | **~25+** |
| Playwright E2E spec files | **8** |
| GitHub Actions workflows | **3** |
| Documented product phases marked complete | **9+** major phases in `docs/IMPLEMENTATION_STATUS.md` |
| Frontend feature areas (route groups) | **~20+** routes (dashboard, bank, close, AR, platform, etc.) |
| `@PreAuthorize` usages on controllers | **~150+** annotations across finance/admin controllers |

*Not verified:* production user count, revenue, uptime SLOs, exact JaCoCo percentage, number of live tenants.

---

## 14. STRONGEST CV MATERIAL (Top 10)

*As sole developer, each row is defensible as personal delivery (design + implementation + tests/docs where listed).*

| Rank | Fact | Why valuable | Evidence | Suitable roles |
| --- | --- | --- | --- | --- |
| 1 | Multi-tenant Spring Boot modular monolith with firm-scoped isolation tested by matrix integration tests | Shows SaaS security and test discipline | `TenantIsolationMatrixIntegrationTest`, `docs/SECURITY.md` | Backend, Java, Architect-track |
| 2 | ~180 REST endpoints across full bookkeeping lifecycle (docs → ledger → bank → close → reports) | Demonstrates domain breadth and API ownership | Controllers under `platform-app/.../controller` | Full Stack, Backend, Solutions Engineer |
| 3 | Idempotency framework for financial POSTs with integration tests | Real-world payment/ledger correctness | `IdempotencyFilter`, `IdempotencyFrameworkIntegrationTest` | Backend, Java |
| 4 | Bank CSV import + fingerprint dedupe + reconciliation concurrency control | Non-trivial fintech data integrity | `BankTransactionFingerprint`, concurrency tests, `V31`, `V38`–`V39` | Backend, Java |
| 5 | Transactional outbox driving optional LLM document extraction | Reliability + AI integration pattern | `OutboxWorker`, `module-ai`, `V32` | Backend, Cloud, Architect-track |
| 6 | RBAC + per-client access matrix enforced in services and E2E | Enterprise authorization modeling | `ClientAccessService`, `e2e/tests/tenant-workflows.spec.ts` | Backend, Full Stack, Security-minded roles |
| 7 | 40 Flyway migrations evolving AR, bank, auth hardening, and reliability tables | Database engineering at product pace | `db/migration/V1`–`V40` | Backend, Java |
| 8 | CI with Testcontainers Postgres suites + Playwright golden-path E2E | Delivery pipeline for regulated-ish domain | `.github/workflows/backend.yml`, `e2e.yml` | Full Stack, Cloud, DevOps-adjacent |
| 9 | Month-end close readiness engine with pluggable checks and closed-period integrity tests | Workflow/orchestration in accounting domain | `PeriodCloseService`, `ClosedPeriodIntegrityIntegrationTest` | Backend, Solutions Engineer |
| 10 | Angular 20 SPA with role guards and separate platform-admin surface | Modern frontend + split admin plane | `app.routes.ts`, `platformGuard` | Full Stack, Frontend |

---

## 15. POSSIBLE CV BULLETS

*First-person / sole-developer framing. Use “I” on CV or “Sole developer:” as your style prefers.*

1. As sole developer, architected and implemented a **multi-tenant modular monolith** (Spring Boot 4, Java 17, PostgreSQL) with **~180 REST handlers** across auth, ledger, documents, bank reconciliation, and month-end close.

2. Enforced **firm- and client-scoped authorization** with JWT reload, four roles, three client access types, and **Postgres integration tests** including a tenant isolation matrix.

3. Built an **idempotent mutation layer** (request fingerprint + `Idempotency-Key`) for financial POSTs, with dedicated integration coverage.

4. Delivered **CSV bank statement import** with file- and row-level duplicate detection (`BankTransactionFingerprint`) and **concurrency-safe reconciliation** claims in PostgreSQL.

5. Implemented **evidence-driven month-end close** (readiness checks, period lifecycle, closed-period write guards) integrated with document requests and bank reconciliation blockers.

6. Integrated **optional AI document extraction** via a **transactional outbox** and async worker, ensuring uploads commit before LLM processing and drafts require human approval.

7. Developed **accounts receivable** flows (sales invoices, payments, allocations, bank-linked settlement) across **Flyway migrations V33–V36** and workflow integration tests.

8. Shipped an **Angular 20** practice workspace (work queue, banking, close command center, AR) with route guards aligned to backend RBAC.

9. Built **CI pipelines** (Gradle unit tests, Testcontainers Flyway + Postgres suites, Playwright E2E on PostgreSQL 16) for end-to-end regression on a solo codebase.

10. Hardened authentication with **BCrypt-12**, refresh-token rotation, session revocation on reuse, auth rate limiting (memory/Redis), and login lockout tests.

11. Authored **40 Flyway migrations** and SQL-first **P&L/reporting aggregations** with CSV/XLSX export for approved ledger data only.

12. Delivered **deployment assets** (Docker Compose, Nginx same-origin/split-domain configs, OCI/VPS runbooks) for independent frontend/API hosting. [VERIFY: production deploy vs. local/CI only]

---

## 16. INTERVIEW TALKING POINTS

| # | Interviewer may ask | Explain | Relevant files |
| --- | --- | --- | --- |
| 1 | How do you prevent cross-tenant data leaks? | JWT `firmId` is not trusted alone; filter reloads user; queries scoped; 404 vs 403; test matrix | `JwtAuthenticationFilter`, `TenantIsolationMatrixIntegrationTest` |
| 2 | How does refresh token security work? | HttpOnly cookie, rotation, reuse detection revokes all sessions; access in memory | `SessionService`, `docs/SECURITY.md`, `SessionRevocationIntegrationTest` |
| 3 | Walk through document upload to approved expense | Upload → storage key → optional outbox → AI DRAFT → review → approve; audit events | `DocumentService`, `DocumentAiOutboxHandler`, `ExpenseService` |
| 4 | Why idempotency keys on POST? | Filter design, body hash, storage, client retry behavior | `IdempotencyFilter`, `IdempotencyService` |
| 5 | How does bank reconciliation suggest matches? | Deterministic scoring, direction rules, confirm/reject, period guards | `BankReconciliationService`, `docs/BANK_RECONCILIATION.md` |
| 6 | What blocks month-end close? | CloseCheck plugins: drafts, unlinked docs, open requests, bank unmatched | `CloseReadinessService`, `docs/CLOSE.md` |
| 7 | How is RBAC different from client access type? | Role caps capability; access type further restricts per client; owners never ledger-write | `ClientAccessService`, `docs/SECURITY.md` |
| 8 | Outbox vs throwing events after commit? | Table + worker; crash recovery; AI handler idempotency | `OutboxWorker`, `EventOutbox` |
| 9 | How are reports kept correct? | APPROVED-only SQL; view `v_approved_transactions`; no draft leakage to auditors | `module-reporting`, `V18` |
| 10 | How would you deploy this? | Separate SPA + API; env secrets; Nginx; health/ready; optional S3 and SMTP | `README.md`, `docs/DEPLOYMENT.md`, `docker-compose.prod.yml` |

---

## 17. INFORMATION YOU STILL NEED FROM ME

**Answered:** Built **solo / sole developer**, full stack (see authorship note at top).

Remaining questions (cannot be inferred from code alone):

1. Was it deployed for **real users**, a **pilot**, **demo only**, or **commercial** SaaS?
2. How long did active development take (months)? Any release or milestone dates?
3. What **measurable outcomes** occurred (firms onboarded, documents processed, close cycles completed)?
4. Any **production incidents** or hard problems you personally solved (performance, security, data fixes)?
5. CV emphasis: **backend**, **full stack**, or **architect**-track?
6. Public name on CV: **Finance Platform**, product brand, or NDA/client alias?

---

*End of report.*
