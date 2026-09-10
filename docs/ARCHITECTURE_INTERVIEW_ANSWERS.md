# Architect Interview Answers — Finance AI Automation Platform

Answers to the 30 questions from the Architecture Mastery plan. Grounded in this repository’s code and schema.

---

### 1. Why is tenancy `firmId` on rows instead of a separate database per firm?

Shared PostgreSQL with `firm_id` is cheaper to operate, easier to migrate (Flyway once), and matches early SaaS scale. Isolation is application-enforced (queries + JWT firm check). DB-per-tenant would strengthen blast-radius isolation but multiply connection pools, migrations, and backup complexity. Postgres RLS would be a later defense-in-depth layer without splitting databases.

### 2. Why reload the user from the DB on every JWT request instead of trusting claims?

Claims can be stale (deactivated user, role change, firm mismatch after bug/compromise). [`JwtAuthenticationFilter`](../backend/module-auth/src/main/java/com/finance/platform/auth/infrastructure/security/JwtAuthenticationFilter.java) loads the user, checks `active` / `deletedAt`, and requires `user.firmId.equals(claims.firmId())`. Authorities come from the DB role via `SecurityUser`, not solely the JWT `role` claim. Cost: one DB read per authenticated request.

### 3. What is the difference between role `ADMIN` and a platform admin grant?

Firm `ADMIN` manages one firm’s users, clients, and books. Platform admin is a separate grant in `platform_admin_grants` (V24), checked by `PlatformAdminService`, exposed under `/api/v1/platform/**`. Platform APIs see firm/subscription metadata only — not client ledgers. A firm admin cannot grant themselves platform power through firm APIs.

### 4. How does `UPLOAD_ONLY` differ from `BUSINESS_OWNER` with FULL access?

`BUSINESS_OWNER` is a role; `UPLOAD_ONLY` is a `user_client_access.access_type`. An upload-only owner can upload and respond to document requests but `ClientAccessService` blocks ledger read/write, reports, and banking. A FULL business owner still cannot approve (approve requires ADMIN/ACCOUNTANT) but can see more of their client’s approved surface depending on endpoint policy.

### 5. Walk through what happens if AI fails after a successful upload.

Upload TX commits `Receipt` as `UPLOADED` (or processing transitions via AI persistence). `DocumentUploadedListener` runs `@Async` after commit; exceptions are logged and swallowed. Document remains without successful extraction; user can call `retry-processing`. Ledger is untouched — no phantom expenses.

### 6. Why is AI persistence `REQUIRES_NEW`?

[`DocumentAiPersistenceService`](../backend/module-ai/src/main/java/com/finance/platform/ai/application/DocumentAiPersistenceService.java) uses `Propagation.REQUIRES_NEW` so AI status updates commit independently of any outer TX and so a failure marking `FAILED` does not roll back unrelated work. The upload TX is already committed before the listener runs.

### 7. Why publish `DocumentUploadedEvent` inside the upload transaction but listen `AFTER_COMMIT`?

Publishing inside the TX registers the event with Spring’s transactional event infrastructure. `@TransactionalEventListener(AFTER_COMMIT)` ensures AI never runs if the insert rolls back. If you listened without AFTER_COMMIT, AI could process a document that never persisted.

### 8. Where would orphan files come from in the upload flow?

`DocumentService.upload` stores the blob first, then inserts the receipt. On persist failure it deletes the object. Orphans occur if the process crashes after `store` and before cleanup, or if delete-on-failure itself fails (logged warn). DB-without-file is avoided by store-first; file-without-DB is the residual risk.

### 9. How does duplicate document detection work, and when is `allowDuplicate` appropriate?

SHA-256 of file bytes; lookup `findFirstByFirmIdAndClient_IdAndChecksumSha256AndDeletedAtIsNull`. If found and `allowDuplicate` is false → `DuplicateDocumentException` with existing id. Allow duplicate when the same PDF is legitimately a second copy (e.g. re-issued receipt) after the user confirms in the UI.

### 10. Why can accepting an AI suggestion never create an APPROVED transaction?

Trust boundary: OCR/LLM output is untrusted. `DocumentReviewService.accept` creates **DRAFT** expense/income for a human (ADMIN/ACCOUNTANT) to approve. Auto-approve would let prompt injection or extraction errors post to the books.

### 11. How does period close prevent ledger mutation, and who can reopen?

`PeriodCloseService.assertPeriodOpen` is called from expense/income/bank write paths. Close sets status `CLOSED` after readiness checks. Reopen requires firm admin (`isAdmin()`), a non-blank reason, and sets `REOPENED` with audit — not a silent undo.

### 12. What makes a period “ready” to close?

`CloseReadinessService` / `evaluate` aggregates blockers (drafts, unmatched bank lines, open document requests, etc.) into `PeriodReadinessResponse`. `close` refuses with `PERIOD_NOT_READY_TO_CLOSE` unless `readiness.ready()` is true.

### 13. How are concurrent confirmed bank reconciliations prevented?

V25 partial unique indexes: one CONFIRMED match per `bank_transaction_id`, per `expense_id`, per `income_id`. Second confirm hits a unique violation → conflict handling. Application status checks alone would race without these indexes.

### 14. Why does subscription quota use pessimistic locking?

Creating clients/users under plan limits must not oversell when two admins click simultaneously. `findByFirmIdForUpdate` (`PESSIMISTIC_WRITE`) serializes quota checks and increments on `firm_subscriptions`.

### 15. `row_version` exists in SQL — is optimistic locking actually active? Prove it.

Yes after Priority 0: entities `Expense`, `Income`, `AccountingPeriod`, `BankTransaction` map `@Version` to `row_version`. Hibernate increments on update; concurrent writers get `ObjectOptimisticLockingFailureException` → HTTP 409 `CONCURRENT_MODIFICATION` via `GlobalExceptionHandler`.

### 16. Is idempotency enforced today? How do you know?

Yes after Priority 0: `IdempotencyFilter` is `addFilterAfter` JWT in `AuthSecurityConfig` (servlet auto-registration still disabled to avoid double registration). CORS allows `Idempotency-Key`. `ApiService` attaches a UUID on protected money POSTs. Replay with same key returns stored response; key reuse with different path/hash → conflict.

### 17. Why disable CSRF, and when would that become unsafe?

API auth is `Authorization: Bearer`, not cookies. Browsers do not auto-attach Bearer tokens on cross-site form posts, so classic CSRF does not apply. It becomes unsafe if you store session/refresh in cookies without CSRF tokens / SameSite strategy.

### 18. Why store tokens in `localStorage`, and what XSS implication follows?

Simple SPA persistence for V1. Any XSS can read `fp.session` and exfiltrate access + refresh tokens. Mitigations: short access TTL, refresh rotation, CSP at the proxy, eventual httpOnly cookie for refresh (documented as future hardening in SECURITY.md).

### 19. How would you detect a forgotten `firmId` predicate in a new query?

Integration tests that create two firms and assert cross-firm IDs return 404/403 (`TenantIsolationIntegrationTest` pattern). Code review checklists; prefer repository method naming that requires firmId; later Postgres RLS as a backstop.

### 20. What does `/health/ready` actually prove in Docker today?

After Priority 0: it opens a DataSource connection and calls `isValid(2)`. Success → 200 READY; failure → 503 NOT_READY. Liveness `/health` remains a process-up signal without DB. Docker HEALTHCHECK should hit ready so traffic is not sent to a DB-disconnected JVM.

### 21. Why is the outbox table present if nothing writes to it?

V25 introduced transactional outbox scaffolding for durable side effects (AI enqueue, email). Live path still uses Spring `ApplicationEventPublisher`. Either wire `OutboxService.append` + a poller, or remove the dead path to reduce confusion — currently a reliability gap, not a feature.

### 22. Classify bank CSV import: sync or async? Should it change at 100k rows?

Today synchronous in the HTTP request (`BankReconciliationService.importCsv`). Fine for small statements. At 100k rows: move to async job + progress API, stream parse, batch inserts, and idempotent import keys — otherwise gateway timeouts and long transactions.

### 23. What fails first at 10k concurrent users in this architecture?

PostgreSQL connections and hot queries; per-request user load in the JWT filter; in-memory rate limiter useless across instances; local disk storage if not on S3; AI provider rate limits; single-node `@Async` queue saturation. Horizontal API helps only after shared rate limiting and externalized jobs.

### 24. How would you add Redis without caching permissions unsafely?

Cache immutable/slowly changing reference data (plans, categories) with firm-scoped keys and short TTL or explicit bust on write. Do **not** cache effective ACLs or auditor draft visibility without versioned invalidation tied to `user_client_access` and role changes. Prefer caching JWT principal metadata for seconds only if you accept slightly stale deactivation.

### 25. Why is this a modular monolith rather than microservices?

One deployable JAR (`platform-app`), shared DB, in-process events. Gradle modules (`module-auth`, `module-finance`, …) organize boundaries without network hops. Microservices would force distributed transactions across documents/ledger/periods — unjustified until team and scale demand independent deploy cadence.

### 26. Where is the trust boundary between `module-ai` and `module-finance`?

AI may suggest fields and move document status to NEEDS_REVIEW; finance review accepts into **DRAFT** only. AI must not call approve APIs or write APPROVED ledger rows. Invalid AI output must still pass domain validation (category, amounts).

### 27. How does auditor visibility of drafts get enforced?

AUDITOR effective access is READ_ONLY; write/approve/upload denied. List endpoints for ledger use status filters / rules so auditors see finalized (approved) data per SECURITY.md. Any new report query must apply the same visibility rules or auditors see drafts.

### 28. What is the blast radius if `APP_JWT_SECRET` leaks?

Attacker can forge access tokens for any `sub`/`firmId`/`role` until secret rotation. Filter still loads the user — forged tokens for deleted users fail — but active users can be impersonated. Refresh tokens remain server-side hashed, but forged access alone is enough for API calls. Rotate secret, invalidate sessions, treat as production incident. `ProductionJwtSecretValidator` blocks weak secrets in prod.

### 29. If the app crashes after approving an expense but before audit insert, what state remains?

Depends on TX boundaries: `ExpenseService.approve` is `@Transactional` and records audit inside the same service method — typically one TX, so crash before commit leaves neither approve nor audit; crash after commit leaves both. If audit used `REQUIRES_NEW` and failed independently, you could have approved state without audit (check `JpaAuditLogger` propagation). Prefer audit in the same TX as the money change for this invariant.

### 30. Which single Priority 0 fix would you ship first for a production firm pilot, and why?

Wire **idempotency + optimistic locking** on money mutations (approve, accept, bank confirm, close). Duplicate submits and concurrent edits are the fastest way to corrupt books in demos and pilots. Ready health and CI tests are close seconds for operational safety, but incorrect ledger rows destroy trust first.
