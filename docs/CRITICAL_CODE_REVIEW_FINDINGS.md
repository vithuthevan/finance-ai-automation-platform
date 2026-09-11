# Critical Code Review — Findings Only

Staff/principal engineering review of the Finance Platform codebase. Flat list sorted Critical → High → Medium → Low.

**Remediated (2026-09):** Critical items **#1–#8** addressed in code (bank row hash, idempotency, email log redaction, httpOnly refresh cookies, JWT weak-secret block, document-review guards + Receipt `@Version`, period-close lock, Angular 20 upgrade). Remaining High/Medium/Low items await selection.

Paths are relative to the repo root.

---

[1] [Severity: Critical] [Bugs & correctness] — Bank `external_row_hash` overflows VARCHAR(64) **[REMEDIATED]**
File(s): [backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java) (~157–163), [BankTransaction.java](../backend/module-finance/src/main/java/com/finance/platform/finance/domain/model/BankTransaction.java) (~73–74), [V21__bank_statement_phase6.sql](../backend/platform-app/src/main/resources/db/migration/V21__bank_statement_phase6.sql), [V26__critical_correctness_fixes.sql](../backend/platform-app/src/main/resources/db/migration/V26__critical_correctness_fixes.sql)
Issue: Import stores `accountId + ":" + sha256Hex` (~101 chars) into a 64-char column.
Why it matters: Inserts fail or truncate so row-level dedupe breaks and bank lines can duplicate or imports abort mid-batch.

[2] [Severity: Critical] [Bugs & correctness] — Idempotency keys regenerated every SPA click **[REMEDIATED]**
File(s): [frontend/src/app/core/services/api.service.ts](../frontend/src/app/core/services/api.service.ts), [IdempotencyFilter.java](../backend/platform-core/src/main/java/com/finance/platform/core/idempotency/IdempotencyFilter.java)
Issue: Frontend always sends a fresh `crypto.randomUUID()`; backend skips the filter when the header is absent.
Why it matters: Double-click approve/create/import/close creates duplicate financial mutations despite “idempotency” infrastructure.

[3] [Severity: Critical] [Security] — Password-reset tokens logged by default email provider **[REMEDIATED]**
File(s): [LoggingEmailService.java](../backend/platform-core/src/main/java/com/finance/platform/core/notification/LoggingEmailService.java) (9–14), [SessionService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/SessionService.java) (~93–101)
Issue: Default `app.email.provider=log` (`matchIfMissing = true`) logs the full email body including the raw reset token.
Why it matters: Anyone with log access can take over any account when SMTP is not configured (common in staging).

[4] [Severity: Critical] [Security] — Access and refresh tokens stored in localStorage **[REMEDIATED]**
File(s): [auth.service.ts](../frontend/src/app/core/auth/auth.service.ts)
Issue: Full session JSON including `refreshToken` is persisted under `fp.session` in `localStorage`.
Why it matters: Any XSS or malicious extension yields long-lived firm takeover via refresh.

[5] [Severity: Critical] [Security] — Well-known local JWT secret not blocked in prod validator **[REMEDIATED]**
File(s): [application-local.yml](../backend/platform-app/src/main/resources/application-local.yml) (16), [ProductionJwtSecretValidator.java](../backend/platform-app/src/main/java/com/finance/platform/config/ProductionJwtSecretValidator.java)
Issue: Dev default Base64 secret is ≥32 chars and absent from `WEAK_SECRETS`, so prod profile accepts it.
Why it matters: Accidental deploy with the public local secret forges JWTs for every tenant.

[6] [Severity: Critical] [Bugs & correctness] — Document review `modify`/`createFromDocument` can create duplicate drafts **[REMEDIATED]**
File(s): [DocumentReviewService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/DocumentReviewService.java)
Issue: `accept` blocks LINKED/REJECTED; `modify` has no status guard; `createFromDocument` allows LINKED; Receipt has no `@Version`.
Why it matters: One document can spawn multiple DRAFT ledger rows, corrupting books and close readiness.

[7] [Severity: Critical] [Bugs & correctness] — Period close readiness TOCTOU race **[REMEDIATED]**
File(s): [PeriodCloseService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/PeriodCloseService.java)
Issue: Close evaluates readiness then saves CLOSED; concurrent ledger/doc/bank writes are not locked with that evaluation.
Why it matters: A period can close while drafts, unmatched bank lines, or unreviewed docs still exist.

[8] [Severity: Critical] [Dependency issues] — Angular 19 is past security EOL **[REMEDIATED]**
File(s): [frontend/package.json](../frontend/package.json)
Issue: As of Sep 2026 Angular 19 security support has ended; active line is newer.
Why it matters: No official CVE patches for the SPA that holds JWTs and drives financial APIs.

[9] [Severity: High] [Security] — BUSINESS_OWNER can mutate ledger vs documented policy
File(s): [ExpenseController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/finance/ExpenseController.java) (61–81), IncomeController (same), [SECURITY.md](SECURITY.md) roles table
Issue: Controllers allow BUSINESS_OWNER create/update/delete; `requireWriteAccess` does not block the role when access is FULL.
Why it matters: Owners can edit firm books contrary to the stated security model.

[10] [Severity: High] [Security] — Auth rate limiting incomplete and returns wrong status
File(s): [AuthRateLimiter.java](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/AuthRateLimiter.java), [AuthenticationService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/AuthenticationService.java) (~42), [GlobalExceptionHandler.java](../backend/platform-core/src/main/java/com/finance/platform/core/exception/GlobalExceptionHandler.java), [SECURITY.md](SECURITY.md)
Issue: Only login is limited; register/forgot/reset/refresh are unlimited; in-memory map; `RateLimitedException` surfaces as 422 not 429.
Why it matters: Credential stuffing, registration spam, and reset flooding work across replicas; clients won’t back off.

[11] [Severity: High] [Security] — Open self-registration creates firm ADMIN with no verification
File(s): [RegistrationController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/auth/RegistrationController.java), [RegistrationService.java](../backend/platform-app/src/main/java/com/finance/platform/application/service/RegistrationService.java)
Issue: `POST /auth/register` is permitAll, creates Firm + ADMIN, no CAPTCHA, rate limit, or email verification.
Why it matters: Abuse, resource exhaustion, and account registration under emails the attacker does not own.

[12] [Severity: High] [Security] — Password change does not revoke refresh sessions
File(s): [UserService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/UserService.java) (~272–288) vs deactivate/`resetPassword` paths that call `revokeAllForUser`
Issue: Changing password updates the hash but leaves existing refresh tokens valid.
Why it matters: Stolen sessions survive a user-initiated password change.

[13] [Severity: High] [Security] — Access JWTs survive password reset until expiry
File(s): [SessionService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/SessionService.java) (~105–120), JWT config (~1h access TTL), no jti denylist
Issue: Reset deletes refresh tokens but outstanding access tokens remain usable.
Why it matters: An attacker with a stolen access token keeps API access after reset for up to the access TTL.

[14] [Severity: High] [Security] — Platform admin bootstrap via persistent env email
File(s): PlatformAdminBootstrapRunner, application.yml platform bootstrap props
Issue: Every startup with `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` set auto-grants platform admin to that user.
Why it matters: Leaving the env set (or registering that email later) silently elevates a tenant user to cross-firm SaaS operator.

[15] [Severity: High] [Security] — Mis-deploying `local` profile yields forgeable JWT + public OpenAPI
File(s): [application.yml](../backend/platform-app/src/main/resources/application.yml) default profile, [application-local.yml](../backend/platform-app/src/main/resources/application-local.yml), [AuthSecurityConfig.java](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/AuthSecurityConfig.java), SecurityPaths.OPENAPI
Issue: Default/local profile uses hardcoded JWT secret and permitAll swagger; prod secret validator is `@Profile("prod")` only.
Why it matters: Shipping with `local` profile exposes a forgeable auth surface and a public API explorer.

[16] [Severity: High] [Security] — Bank CSV upload skips MIME/magic validation used for documents
File(s): [BankController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/finance/BankController.java), [BankReconciliationService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java) (~99–125) vs DocumentService.validateFile
Issue: Bank imports accept arbitrary bytes with only emptiness checks; original filename stored with weaker sanitization than documents.
Why it matters: Large/malicious payloads and unsafe stored filenames bypass the document security pipeline.

[17] [Severity: High] [Bugs & correctness] — Bank import/checksum uniqueness only enforced in app
File(s): [BankReconciliationService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java) (~112–163), V21 indexes (non-unique)
Issue: Check-then-insert for file checksum and row hash races; DB indexes are non-unique.
Why it matters: Concurrent imports can double-count statement lines and break reconciliation.

[18] [Severity: High] [Bugs & correctness] — Create expense/income from bank ignores current match status
File(s): [BankReconciliationService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java) (~392–452)
Issue: No guard against already MATCHED / PENDING_APPROVAL / IGNORED; multiple drafts can hang off one bank line.
Why it matters: Orphan drafts and permanent pending-approval close blockers.

[19] [Severity: High] [Architecture & design] — Event outbox table/service unused; AI still fire-and-forget
File(s): [OutboxService.java](../backend/platform-core/src/main/java/com/finance/platform/core/outbox/OutboxService.java), [V25__correctness_and_reliability.sql](../backend/platform-app/src/main/resources/db/migration/V25__correctness_and_reliability.sql), DocumentUploadedListener
Issue: Nothing calls `OutboxService.append`; no poller; actual path is in-process `@Async` after commit.
Why it matters: Crash after commit can permanently lose AI extraction/notifications despite “reliability” schema.

[20] [Severity: High] [Testing gaps] — CI skips frontend tests and often skips Postgres security suites
File(s): [.github/workflows/frontend.yml](../.github/workflows/frontend.yml), [.github/workflows/backend.yml](../.github/workflows/backend.yml)
Issue: Frontend CI only `npm install` + build (no test/lint/audit/`npm ci`); backend has no Docker/Postgres service so Testcontainers suites skip.
Why it matters: Green CI can ship without running tenant-isolation, JWT, upload, or SPA unit tests.

[21] [Severity: High] [DevOps/config] — No metrics, tracing, or structured request logging
File(s): [application.yml](../backend/platform-app/src/main/resources/application.yml) logging, platform-app build.gradle (no Actuator/Micrometer), [HealthController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/HealthController.java)
Issue: Custom health exists but no Prometheus, distributed tracing, error tracking, or MDC requestId on every log line.
Why it matters: Cannot alert on 5xx, latency, AI failures, or quota saturation in production.

[22] [Severity: High] [DevOps/config] — Documented security headers missing from shipping Nginx config
File(s): [deploy/nginx/same-origin.conf](../deploy/nginx/same-origin.conf), [SECURITY.md](SECURITY.md) (~72–80)
Issue: No HSTS, CSP, X-Content-Type-Options, Permissions-Policy on SPA responses despite SECURITY.md guidance.
Why it matters: Clickjacking/MIME/XSS blast radius larger than operators are told.

[23] [Severity: High] [Frontend] — Auth guards only check token presence, not validity
File(s): [auth.guard.ts](../frontend/src/app/core/auth/auth.guard.ts), [auth.service.ts](../frontend/src/app/core/auth/auth.service.ts)
Issue: `authGuard` is `!!accessToken` from storage with no expiry parse.
Why it matters: Expired/stale sessions briefly open protected routes and confuse UX/security boundaries.

[24] [Severity: High] [Frontend] — Multi-tab refresh rotation races
File(s): [auth.interceptor.ts](../frontend/src/app/core/interceptors/auth.interceptor.ts), [auth.service.ts](../frontend/src/app/core/auth/auth.service.ts) (~47–62)
Issue: In-tab `shareReplay` is fine; cross-tab concurrent refresh with rotating tokens can revoke the other tab.
Why it matters: Users with multiple firm tabs get unexplained forced logouts.

[25] [Severity: High] [Data & schema] — V25 silently deletes duplicate confirmed reconciliation matches
File(s): [V25__correctness_and_reliability.sql](../backend/platform-app/src/main/resources/db/migration/V25__correctness_and_reliability.sql) (~3–25)
Issue: Migration DELETEs duplicate CONFIRMED matches (keeps lower id) before creating unique indexes.
Why it matters: Production data loss with no audit of which confirmed links were discarded.

[26] [Severity: High] [Bugs & correctness] — Idempotency fingerprint ignores body; many mutating POSTs not covered
File(s): [IdempotencyFilter.java](../backend/platform-core/src/main/java/com/finance/platform/core/idempotency/IdempotencyFilter.java) (28–58)
Issue: Hash is only method+URI; upload, review/modify, bank create-expense/income, reopen, etc. are outside PROTECTED regex; stuck STARTED keys block forever after crash.
Why it matters: False safe retries, wrong cached responses, and double-submit on uncovered money paths.

[27] [Severity: High] [Security] — Linking evidence on APPROVED (including closed periods) mutates finalized books
File(s): [DocumentService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/DocumentService.java) link/unlink paths
Issue: Evidence graph on APPROVED transactions can change post-close when amounts stay the same; unlink rules uneven vs VOID.
Why it matters: Audit/reporting document-support and close completeness can change after books were “final.”

[28] [Severity: Medium] [Security] — Refresh-token rotation without reuse detection
File(s): [SessionService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/SessionService.java) refresh path, RefreshToken entity
Issue: Presenting an already-revoked refresh token does not revoke the token family.
Why it matters: Stolen-token races are hard to detect; compromise can go unnoticed.

[29] [Severity: Medium] [Security] — Multiple concurrent password-reset tokens remain valid
File(s): [SessionService.java](../backend/module-auth/src/main/java/com/finance/platform/auth/application/service/SessionService.java) requestPasswordReset
Issue: Each forgot-password creates a new token without invalidating prior unused tokens.
Why it matters: Expands theft window via email/logs and concurrent reset attempts.

[30] [Severity: Medium] [Security] — AI category attach uses `findById` without firmId
File(s): DocumentAiPersistenceService (~103–104), CategoryJpaRepository
Issue: Suggested category loaded by bare ID with no tenant scope.
Why it matters: Malicious/buggy AI output can attach a cross-tenant category onto a receipt.

[31] [Severity: Medium] [Security] — Several bank/ledger lookups omit firmId (defense-in-depth)
File(s): [BankReconciliationService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/service/BankReconciliationService.java) (~280–323) vs ExpenseService.findExpense firm-scoped query
Issue: Expense/income/match loaded by client/id or id alone after a prior access check.
Why it matters: A missing guard becomes cross-tenant IDOR more easily than firm-scoped queries.

[32] [Severity: Medium] [Security] — Native SQL table-name concatenation in reporting/close readiness
File(s): CloseReadinessQueryRepository, ReportingQueryRepository
Issue: Table/join names concatenated into native SQL; callers pass constants today but APIs accept raw strings.
Why it matters: Future untrusted callers become SQL injection; no allowlist enforced inside the repository.

[33] [Severity: Medium] [Security] — Unauthenticated readiness probe opens DB connections
File(s): [HealthController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/HealthController.java), AuthSecurityConfig permitAll health
Issue: `/api/v1/health/ready` is public and pings PostgreSQL.
Why it matters: Unauthenticated DB load and infrastructure fingerprinting.

[34] [Severity: Medium] [Security] — Weak password policy (min 8, no complexity)
File(s): RegisterRequest, CreateUserRequest, ChangePasswordRequest, SessionService.resetPassword
Issue: Only `@Size(min = 8)`; no complexity or breach checks.
Why it matters: Easier guessing when combined with incomplete rate limits.

[35] [Severity: Medium] [Data & schema] — Optimistic locking incomplete across concurrent domains
File(s): Expense/Income/AccountingPeriod/BankTransaction have `@Version`; Receipt, ReconciliationMatch, BankImport lack it
Issue: Concurrent accept/reject/AI updates on receipts and some match paths are not versioned.
Why it matters: Lost updates and duplicate side effects under concurrent accountants.

[36] [Severity: Medium] [Data & schema] — Soft-delete semantics inconsistent across entities
File(s): DocumentService.delete (soft then hard-deletes blob), UserService deactivate, Client/Category deleted_at columns unused, Expense/Income hard delete
Issue: Mixed hard/soft models; soft-deleted receipts permanently lose storage objects.
Why it matters: Non-restorable “soft” deletes, orphan joins, and filters that never match real soft-deleted rows.

[37] [Severity: Medium] [Data & schema] — Document checksum uniqueness only application-enforced
File(s): DocumentService upload (~93–98), receipt checksum indexes non-unique
Issue: Concurrent uploads of identical content can race past the duplicate check.
Why it matters: Duplicate evidence rows and unreliable POSSIBLE_DUPLICATE behavior.

[38] [Severity: Medium] [Performance] — N+1 loads in list mappers and work queue
File(s): FinanceMapper, DocumentService.toResponse, BankReconciliationService.toResponse, PeriodCloseService.workQueue
Issue: Per-row lazy loads and per-client full readiness evaluation inside transactional lists.
Why it matters: Latency/timeouts as firm client and document volume grows.

[39] [Severity: Medium] [Bugs & correctness] — AI quota check unlocked vs locked document upload quota
File(s): [SubscriptionAccessService.java](../backend/module-finance/src/main/java/com/finance/platform/finance/application/subscription/SubscriptionAccessService.java) (~159–177 vs locked upload asserts)
Issue: `canProcessAi` is a non-locking read while document quotas use `FOR UPDATE`.
Why it matters: Concurrent AI jobs can exceed monthly AI allowance.

[40] [Severity: Medium] [Architecture & design] — God pages and duplicated expense/income UI
File(s): [shell.component.ts](../frontend/src/app/layout/shell.component.ts), banking.page.ts, period-detail.page.ts, document-review.page.ts, expenses.page.ts / income.page.ts
Issue: Fat pages with role nav, CRUD, approve/void, and near-duplicate ledger UIs using `any`.
Why it matters: High regression risk and inconsistent RBAC UX vs backend.

[41] [Severity: Medium] [Architecture & design] — Frontend route guards incomplete vs backend RBAC
File(s): [app.routes.ts](../frontend/src/app/app.routes.ts) (documents, notifications, profile often auth-only)
Issue: Several routes rely only on API 403s without role guards.
Why it matters: Wrong-role users hit confusing empty/error states if UI assumes capability.

[42] [Severity: Medium] [Testing gaps] — Critical money paths largely untested
File(s): frontend only [auth.service.spec.ts](../frontend/src/app/core/auth/auth.service.spec.ts); backend tests thin vs close/bank/idempotency/AI
Issue: No E2E Document→Approve→Reconcile→Close→Report; no interceptor/idempotency/close race tests; frontend nearly untested.
Why it matters: Regressions in the core product loop ship undetected.

[43] [Severity: Medium] [Documentation gaps] — Ops docs contradict running code
File(s): PRODUCTION_READINESS.md (actuator/health, V24, JWT_SECRET naming), IMPLEMENTATION_STATUS “builds not executed”, interview banks still V24-centric
Issue: Checklist and status docs disagree with custom `/api/v1/health`, V25, and current CI.
Why it matters: Operators follow wrong health URLs and migration ceilings during incidents.

[44] [Severity: Medium] [DevOps/config] — Compose/.env.example invite weak shared defaults
File(s): [.env.example](../.env.example), [docker-compose.yml](../docker-compose.yml)
Issue: `change-me` DB password defaults and dual `APP_STORAGE_*` / `STORAGE_*` aliases.
Why it matters: Common misconfiguration class for “prod-like” deploys.

[45] [Severity: Medium] [World-class gaps] — No feature flags / limited graceful degradation
File(s): AI config flags in application.yml; no general feature-flag layer; OpenAI without circuit breaker
Issue: Can disable AI, but not banking import, close, or reporting under incident without redeploy.
Why it matters: Incidents force full outage or hotfixes instead of kill-switches.

[46] [Severity: Medium] [Code quality] — Ad-hoc QA scripts embed secrets and machine paths
File(s): [qa-run-tc001-027.ps1](../qa-run-tc001-027.ps1) (and sibling runners)
Issue: Hardcoded local paths, well-known JWT secret, weak passwords; schema comments still say v24.
Why it matters: Non-portable false-confidence suite that can leak secrets into shells/CI copies.

[47] [Severity: Medium] [Architecture & design] — Lombok `@Setter` on Expense/Income status bypasses domain methods
File(s): [Expense.java](../backend/module-finance/src/main/java/com/finance/platform/finance/domain/model/Expense.java), Income.java
Issue: Public setters coexist with `approve`/`void` and TransactionStatusRules used only by convention in services.
Why it matters: Future `setStatus(...)` silently bypasses the state machine.

[48] [Severity: Medium] [Data & schema] — Client unique name vs soft-delete mismatch
File(s): V2 UNIQUE(firm_id, name), ClientService exists…DeletedAtIsNull checks; soft-delete never set
Issue: Unique constraint includes soft-deleted rows if soft-delete is ever used; app filters disagree.
Why it matters: Name reuse blocked or uniqueness races depending on unfinished soft-delete design.

[49] [Severity: Medium] [Bugs & correctness] — No auto-confirm when bank-created draft is later approved
File(s): Bank recon docs (known limitation), BankReconciliationService create-from-bank paths
Issue: Bank line stays PENDING_APPROVAL after linked DRAFT is approved through normal ledger flow.
Why it matters: Close blockers persist after books are actually approved.

[50] [Severity: Medium] [World-class gaps] — API versioning is path-only with no deprecation/compatibility policy
File(s): Controllers under `/api/v1`, no changelog/version negotiation
Issue: Single `/api/v1` with breaking changes shipped in-place across phases.
Why it matters: External/Postman/clients break silently; no staged migration story.

[51] [Severity: Medium] [World-class gaps] — Manual SaaS billing with no payment integrity controls
File(s): ManualBillingProvider, PlatformController subscription endpoints, PlanChangeRequest
Issue: Plan/status changes are operator-driven with no payment webhook reconciliation.
Why it matters: Acceptable for V1 but commercially fragile (disputes, accidental SUSPENDED, privilege errors).

[52] [Severity: Medium] [Performance] — Synchronous AI/path timeouts without queue depth limits
File(s): DocumentAiProcessor, AsyncConfig, AI timeout props
Issue: `@Async` extraction with timeout/retry but no bounded queue metrics or backpressure to upload.
Why it matters: AI outage or spike can exhaust thread pools and degrade the whole app.

[53] [Severity: Low] [Security] — `@PreAuthorize` broader than service rules on GET /users/{id}
File(s): [UserController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/user/UserController.java), UserService.assertSelfOrAdmin
Issue: Annotation allows multiple roles; service denies non-self non-admin.
Why it matters: Confusing surface; risk if a future path skips the service check.

[54] [Severity: Low] [Security] — AuthController `/me` lacks method-level `@PreAuthorize`
File(s): [AuthController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/auth/AuthController.java)
Issue: Relies only on filter-chain `authenticated()`.
Why it matters: Weaker defense-in-depth if chain rules change.

[55] [Severity: Low] [Bugs & correctness] — Null refresh/logout body can NPE
File(s): [LoginController.java](../backend/platform-app/src/main/java/com/finance/platform/controller/auth/LoginController.java) (27–34), SessionService sha256
Issue: No null checks before hashing refresh tokens.
Why it matters: Unauthenticated 500s on malformed clients.

[56] [Severity: Low] [Security] — Weak WebP/CSV magic checks on documents
File(s): DocumentService.mimeMatches
Issue: WebP only checks RIFF; CSV accepts any bytes with `.csv`.
Why it matters: Polyglot uploads within allowed types.

[57] [Severity: Low] [Security] — CSRF disabled (Bearer-only today)
File(s): [AuthSecurityConfig.java](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/AuthSecurityConfig.java) (~52)
Issue: CSRF off; acceptable for Authorization header, fragile if cookies are added later.
Why it matters: Cookie migration without CSRF becomes Critical.

[58] [Severity: Low] [Data & schema] — DB allows tax_amount > amount
File(s): V4 ledger CHECKs, ExpenseService/IncomeService validateAmounts
Issue: Constraint only ensures tax ≥ 0; app validates relative size.
Why it matters: Direct DB/API bypass of app validation can store nonsensical tax.

[59] [Severity: Low] [DevOps/config] — Frontend CI uses `npm install` not `npm ci`
File(s): [.github/workflows/frontend.yml](../.github/workflows/frontend.yml)
Issue: Non-reproducible dependency resolution.
Why it matters: Supply-chain/drift between lockfile and what CI builds.

[60] [Severity: Low] [Code quality] — Preview uses bypassSecurityTrustResourceUrl
File(s): document-review.page.ts (~352)
Issue: Blob URLs from own API are DomSanitizer-bypassed.
Why it matters: Safe only while content origin/MIME remain tightly controlled.

[61] [Severity: Low] [Documentation gaps] — Receipt vs Document naming throughout codebase
File(s): Receipt entity/table `receipts`, API/docs “documents”
Issue: Dual vocabulary for one concept.
Why it matters: Onboarding mistakes and accidental parallel “Document” tables.

[62] [Severity: Low] [World-class gaps] — No antivirus scanning on uploads
File(s): DocumentService, IMPLEMENTATION_STATUS known limitations
Issue: MIME sniff + size only; no malware scanning.
Why it matters: Malicious PDFs/images stored and previewed by accountants.

[63] [Severity: Low] [World-class gaps] — No PDF report generation / no persisted close snapshots
File(s): IMPLEMENTATION_STATUS Phase 3/5 remaining
Issue: Exports are CSV/XLSX only; closed-period reports are live queries.
Why it matters: Accountant deliverables and historical freeze weaker than peer products.

[64] [Severity: Low] [World-class gaps] — In-process schedulers only (no distributed lock)
File(s): SubscriptionMaintenanceScheduler, OverdueDocumentRequestScheduler
Issue: Multi-instance deploys can double-run maintenance/reminders.
Why it matters: Duplicate emails/notifications and racey subscription transitions.

[65] [Severity: Low] [Code quality] — Large generated interview banks mixed with product docs
File(s): JAVA_SPRING_BOOT_PROJECT_INTERVIEW_BANK.md, ARCHITECTURE_*, docs/__pycache__ (ignored)
Issue: Very large generated Q&A alongside operational docs.
Why it matters: Noise for contributors; risk of treating interview prose as source of truth.

[66] [Severity: Low] [DevOps/config] — Liveness always UP by design
File(s): HealthController.health
Issue: Liveness does not check dependencies (ready does).
Why it matters: Fine if LBs use ready; miswiring liveness alone keeps unhealthy pods in rotation.

[67] [Severity: Low] [Architecture & design] — module-finance depends on auth domain models directly
File(s): Expense/Income/Receipt importing `com.finance.platform.auth.domain.model.User`
Issue: Cross-module entity coupling vs ports used elsewhere (FirmStaffPort).
Why it matters: Harder to extract modules later; inconsistent hexagonal boundaries.

[68] [Severity: Low] [Bugs & correctness] — Period reopen has no idempotency coverage
File(s): IdempotencyFilter PROTECTED regex, PeriodController reopen
Issue: Reopen POST is outside idempotent path set.
Why it matters: Double-submit reopen is lower risk than close but still noisy/audity.

[69] [Severity: Low] [Testing gaps] — PowerShell QA results treated as regression artifacts
File(s): qa-results-tc*.csv/md committed
Issue: Checked-in manual run outputs without CI gating or environment reproducibility.
Why it matters: Stale pass/fail signals in git history.

[70] [Severity: Low] [World-class gaps] — No secondary auth for platform-admin actions
File(s): PlatformController grant/plan/status endpoints
Issue: Platform admin is a persisted grant with same JWT session as firm work; no step-up MFA.
Why it matters: Compromised platform-admin session can change every firm’s plan/status.

---

Await item number(s) to dig into and fix.
