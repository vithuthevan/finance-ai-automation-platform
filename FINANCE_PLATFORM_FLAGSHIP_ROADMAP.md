# Finance Platform — Flagship Implementation Roadmap

**Companion to:** `FINANCE_PLATFORM_FLAGSHIP_AUDIT.md`  
**Principle:** Evolve the existing modular monolith. No parallel app. Minimal diffs per phase. No Kafka / microservices / Redis cache unless a phase explicitly requires it.

Phases are ordered for **financial integrity and tenant trust first**, then **unified operations UX**, then **intelligence**.

---

## PHASE 1 — Financial correctness + tenant/security foundation

| | |
|--|--|
| **Objective** | Make the platform safe to demo to security-conscious firms and immune to common money-duplication races. |
| **Business value** | Prevents reputational loss from cross-tenant leaks, duplicate bank lines, or double-submit approvals. |
| **Complexity** | **L** |

### Architecture changes

- Expand **idempotency** coverage (filter paths + request body fingerprint for uploads where feasible).
- Add **DB unique constraints** for bank import file checksum and row hash (firm + account scoped).
- Formalize **tenant isolation test matrix** generated from controller inventory.

### Backend work

- `IdempotencyFilter` — extend `PROTECTED` patterns; include body hash for JSON POSTs; TTL/reaper for stuck `STARTED`.
- `BankReconciliationService` — guards on `createExpenseFromBank` / `createIncomeFromBank` for MATCHED/IGNORED/PENDING.
- Flyway migration: unique indexes on bank dedupe columns (align with V31 intent).
- `BankController` / import path — align validation with `DocumentService.validateFile` (size, content sniff).
- `AuthRateLimiter` — register/forgot/reset/refresh; return **429**.
- `SessionService` — revoke refresh family on password change; document access-token TTL risk.
- `DocumentAiPersistenceService` — load categories with `firmId` scope.
- Parameterized `TenantIsolationIntegrationTest` extensions (bank, documents, periods, AR, matches).

### Frontend work

- Verify `ApiService` idempotency keys on all financial POSTs (extend regex if gaps).
- `auth.guard.ts` — optional JWT `exp` check to avoid stale route access.
- No new features.

### Database work

- V36+ migration: unique constraints, any `external_row_hash` length fixes if still pending in envs.

### Tests

- Integration: concurrent bank import test; idempotency replay test; IDOR matrix.
- Unit: bank match-status guard tests.

### Migration concerns

- Unique index creation may fail on existing duplicate rows — data cleanup script + audit log entry before constraint.

### Risks

- Production duplicates must be reconciled manually before unique indexes.
- Broader rate limits may affect legitimate bulk operations — tune thresholds.

### Acceptance criteria

- [ ] CI runs Postgres Testcontainers on every backend PR (no silent skip).
- [ ] IDOR test covers ≥95% of authenticated `{uuid}` routes.
- [ ] Double-submit approve/close/import returns same result, not duplicate rows.
- [ ] Concurrent bank import does not duplicate row hashes.
- [ ] Critical review High items #9–#18 either fixed or documented with compensating control.

---

## PHASE 2 — Unified workflow + Work Queue (command centre)

| | |
|--|--|
| **Objective** | One operational home for accountants: what needs attention, by client, with deep links. |
| **Business value** | Firms managing 50+ clients see portfolio risk without opening every module. |
| **Complexity** | **M** |

### Architecture changes

- Treat **Work Queue as product spine**; dashboard becomes summary of work API.
- Optional: expand `practice_work_assignments` for assign/complete without duplicating source data.

### Backend work

- `PracticeWorkQueueService` — add types: `FAILED_BANK_IMPORT`, `CLOSE_BLOCKER` (from readiness), sorting by priority/age.
- `PracticeWorkQueryRepository` — optimize queries; avoid N+1 in portfolio.
- API: `GET /api/v1/work/summary` enrich with reconciliation % and document review % (reuse close queries).
- `MonthEndCommandCenterService` — align DTOs with work queue for single source.

### Frontend work

- Enhance `work.page.ts` — filters, client column, age, assignee; default sort priority.
- `dashboard.page.ts` — redirect accountants to work-first layout; **Needs Attention** section from `WorkSummaryResponse`.
- Shared components: `StatusBadge`, `EmptyState`, `PageHeader` (extract from existing patterns).

### Database work

- Indexes on work query paths if explain plans show seq scans.

### Tests

- `MonthEndCommandCenterIntegrationTest` extended; work queue pagination test.

### Migration concerns

- None.

### Risks

- Performance on large firms — cap portfolio page size; cache readiness per client briefly (in-request only, not Redis).

### Acceptance criteria

- [ ] Accountant lands on actionable list &lt;3 clicks from login.
- [ ] Portfolio shows per-client: doc review %, recon %, blocker count, close status.
- [ ] Every work row deep-links to resolving screen.

---

## PHASE 3 — Close-readiness + enhanced reconciliation

| | |
|--|--|
| **Objective** | Close and bank recon feel like engineered products, not checkbox features. |
| **Business value** | Faster month-end, fewer missed unmatched lines, defensible close sign-off. |
| **Complexity** | **L** |

### Architecture changes

- **Reconciliation Stage 2** in `ReconciliationSuggestionService` — fuzzy amount bands, partial payments (behind feature flag).
- Use `reconciliation_match_groups` for N:1 when partial payments enabled.

### Backend work

- `CloseReadinessService` — expose typed blocker enum stable for API consumers.
- `PeriodCloseService` — optional **close snapshot** JSON column or `period_close_snapshots` table (blocker counts, recon %, doc % at close time).
- `BankReconciliationService` — wire match groups for partial allocation.
- Reports: `GET .../reports/close-readiness` and `.../reports/unreconciled` (read-only).

### Frontend work

- `period-detail.page.ts` — blocker list with drill-down links.
- `banking.page.ts` — show score components prominently; partial match UI when enabled.

### Database work

- Migration for snapshot table or period column; match group usage indexes.

### Tests

- Golden path: import → suggest → partial match → close blocked → resolved → close.
- Close snapshot immutability test.

### Migration concerns

- Snapshot optional — backfill not required.

### Risks

- Fuzzy matching false positives — require human confirm; never auto-confirm.

### Acceptance criteria

- [ ] Close blocked when unmatched bank lines exist (existing) with UI drill-down.
- [ ] Partial payment match explains components summing to bank amount.
- [ ] Close snapshot retrievable for closed periods.

---

## PHASE 4 — Audit + observability + reliability

| | |
|--|--|
| **Objective** | Production operability and auditor-grade traceability. |
| **Business value** | Support incidents without guessing; sell to firms with compliance expectations. |
| **Complexity** | **M** |

### Architecture changes

- **OpenTelemetry** SDK + Micrometer bridge (no Kafka).
- Structured audit for AI and integration events.

### Backend work

- OTel instrumentation: HTTP, JDBC, outbox handler, AI provider calls.
- Metrics: document processing failures, bank import failures, AI latency, outbox backlog depth.
- `AuditAction` extensions: `AI_CATEGORY_PROPOSED`, `AI_CATEGORY_ACCEPTED`, `AI_CATEGORY_OVERRIDDEN`.
- Outbox: failed event visibility + admin replay endpoint (platform admin).
- Nginx: HSTS, CSP baseline per `SECURITY.md`.

### Frontend work

- Correlation id display in error toast (support).
- Audit UI filters for AI-assisted events.

### Database work

- Index `audit_log (firm_id, created_at)` if not present; partition strategy doc only (no implement until volume).

### Tests

- Smoke test that trace context propagates MDC → logs.

### Migration concerns

- None.

### Risks

- CSP breaking inline styles in Angular — test staging.

### Acceptance criteria

- [ ] Grafana/dashboard JSON or documented Prometheus queries for golden signals.
- [ ] AI-assisted actions queryable in audit API.
- [ ] Outbox failures visible and replayable.

---

## PHASE 5 — AI suggestions + anomaly detection

| | |
|--|--|
| **Objective** | System proposes; humans approve. Exceptions surface automatically. |
| **Business value** | Differentiation vs. generic trackers; faster review without silent mutations. |
| **Complexity** | **L** |

### Architecture changes

- `financial_anomalies` table + `AnomalyDetectionService` (rules engine).
- `AiRecommendation` record (or extend `document_processing_attempts` metadata) for proposals.

### Backend work

- Flyway: `financial_anomalies`, `ai_usage_records` (tokens, model, feature, estimated cost).
- Rules: duplicate expense, large txn, supplier spike, stale unreconciled, MoM variance.
- Hook: on approve, bank import complete, scheduled daily scan (scheduler, not new broker).
- `DocumentReviewService` — persist proposal payload before accept; audit overrides.
- `module-ai` — record token usage from provider responses into `ai_usage_records`.

### Frontend work

- Anomaly panel on work queue and period detail.
- AI recommendation card on document review (enhance existing).

### Database work

- Anomaly indexes: `(firm_id, client_id, status, detected_at)`.

### Tests

- Rule unit tests with fixtures; anomaly → work queue integration.

### Migration concerns

- Backfill not required.

### Risks

- Alert fatigue — severity thresholds configurable per firm later.

### Acceptance criteria

- [ ] Anomalies explain **why** in plain language.
- [ ] AI category suggestion stored with model id + user decision.
- [ ] Admin can see AI usage totals per firm.

---

## PHASE 6 — AI Finance Copilot

| | |
|--|--|
| **Objective** | Natural language over **approved tools** only. |
| **Business value** | Interview-grade “controlled AI over finance ops data.” |
| **Complexity** | **L** |

### Architecture changes

```
User → CopilotController → CopilotOrchestrator → FinanceToolRegistry → existing services
```

### Backend work

- `module-ai`: `CopilotService`, tool definitions with JSON schema for LLM function calling.
- Tools: period summary, unreconciled, close blockers, variance, supplier spend, document search, work summary.
- Enforce `ClientAccessService` per tool argument; AUDITOR read-only tool subset.
- Rate limit copilot per firm; log prompts (redacted) to audit.

### Frontend work

- Copilot drawer on dashboard/work/close pages; cite entity links in answers.

### Database work

- Optional `copilot_conversations` — start stateless (no history) for v1.

### Tests

- Tool authorization tests (cross-client id rejected).
- Golden questions integration test with mock LLM.

### Migration concerns

- None.

### Risks

- LLM hallucination — require tool grounding; refuse when data missing.

### Acceptance criteria

- [ ] “What is blocking August close?” returns real blocker DTO data.
- [ ] No raw SQL from model; all answers traceable to tool outputs.
- [ ] Copilot cannot approve or mutate ledger.

---

## PHASE 7 — External accounting integrations

| | |
|--|--|
| **Objective** | Push approved data to external GL without corrupting local state. |
| **Business value** | Firms that keep QBO/Xero as GL can still use document-to-close here. |
| **Complexity** | **XL** |

### Architecture changes

- Implement `AccountingProvider` port; wire `integration_connections` entity.
- Sync jobs via **outbox** (same worker), not Kafka.

### Backend work

- Entity + repository for `integration_connections`.
- One adapter (e.g. Xero or CSV export bundle) as pilot.
- Idempotent push with external id stored on expense/income rows.
- UI: connection test, sync status, last error.

### Frontend work

- Firm settings → Integrations tab.

### Database work

- Use V33 tables; add columns if OAuth tokens need encryption metadata.

### Tests

- Contract tests with provider sandbox; failure does not roll back local APPROVED.

### Migration concerns

- Encrypt tokens at rest (app-level or DB).

### Risks

- OAuth scope creep; rate limits — circuit breaker per connection.

### Acceptance criteria

- [ ] Push expense creates external entry with retry.
- [ ] Failed push leaves local state APPROVED and shows FAILED sync status.
- [ ] Disconnect revokes tokens.

---

## Phase dependency graph

```
PHASE 1 (security/correctness)
    ↓
PHASE 2 (work queue / command centre)
    ↓
PHASE 3 (close + recon depth) ──→ PHASE 4 (observability)
    ↓                                    ↓
PHASE 5 (anomalies + AI proposals) ←─────┘
    ↓
PHASE 6 (copilot)
    ↓
PHASE 7 (integrations)
```

Phases 4 and 5 can overlap slightly after Phase 2 if staffing allows, but **do not start Copilot (6) before tool-grade data (3–5) is trustworthy**.

---

## What we explicitly will NOT do (unless requirements change)

| Item | Reason |
|------|--------|
| Microservices split | No operational bottleneck in monolith |
| Kafka | Outbox + schedulers sufficient |
| Redis application cache | Financial reads need authoritative DB |
| Vector RAG for copilot v1 | Structured tools suffice |
| Full GL / double-entry | Out of product scope |
| Autonomous AI approvals | Violates trust model |

---

## Effort summary

| Phase | Size | Calendar hint (1 senior + 1 mid) |
|-------|------|----------------------------------|
| 1 | L | 3–5 weeks |
| 2 | M | 2–3 weeks |
| 3 | L | 4–6 weeks |
| 4 | M | 2–4 weeks |
| 5 | L | 4–5 weeks |
| 6 | L | 3–5 weeks |
| 7 | XL | 8–12+ weeks per provider |

---

## First implementation kickoff (when approved)

Start **Phase 1 only**. Deliverables per implementation slice:

1. Current behaviour (from code)
2. Problem statement
3. Minimal design
4. Files touched list
5. Implementation + tests
6. Changelog note
7. Residual risks

**Do not** rename modules or move packages for aesthetics.
