# Implementation Status

## Phase 1 — Backend Foundation

**Status:** complete

Firm registration, user administration, client/category lifecycle, draft/approve/void ledger, auditor draft isolation, tenant-scoped audit query, and pagination are in place. Access types are enforced at service level; JWT tenant claims are checked against the database user.

## Phase 2 — Document & Receipt Management

**Status:** complete

The existing `Receipt` document layer is now a complete evidence workflow without AI: authenticated upload, local/S3 storage, inbox, review, manual draft creation, linking, rejection, controlled deletion, and role-aware frontend.

### Completed

- Firm/client-scoped multipart upload with SHA-256 checksum, MIME sniffing, safe storage keys `firms/{firmId}/clients/{clientId}/documents/{uuid}`
- Local filesystem and S3-compatible `FileStorageService`; provider selected by configuration only
- Lifecycle `UPLOADED` / `PROCESSING` / `EXTRACTED` / `NEEDS_REVIEW` / `LINKED` / `REJECTED` / `FAILED` (AI statuses preserved, not falsely marked extracted)
- Document types including `CREDIT_NOTE`; review metadata (`reviewedBy`, `reviewedAt`, `reviewNote`, `description`)
- Paginated inbox and per-client list with type/status/date/linked filters; page size capped at 100
- Secure download/preview (`/content`) with tenant + client + role checks; missing objects return `DOCUMENT_OBJECT_MISSING`
- Duplicate warning `POSSIBLE_DUPLICATE` (not a hard block); `allowDuplicate` to keep a second copy
- Manual create-expense/income from a document stays `DRAFT` / `MANUAL`; existing transactions can be linked
- Cross-firm and cross-client linking blocked (`DOCUMENT_CLIENT_MISMATCH`)
- Unlink (DRAFT freely; APPROVED audited; VOID evidence stays); delete only when not supporting APPROVED/VOID
- `UPLOAD_ONLY` can upload and see own documents; no ledger/reports; `READ_ONLY` cannot upload; auditor sees finalized evidence only
- Angular inbox, review/preview, upload, transaction attachments, upload-only navigation
- Audit: `DOCUMENT_UPLOADED`, `DOCUMENT_DOWNLOADED`, `DOCUMENT_REVIEWED`, `DOCUMENT_REJECTED`, `DOCUMENT_LINKED`, `DOCUMENT_UNLINKED`, `DOCUMENT_DELETED`
- Flyway `V17__document_review_and_indexes.sql`
- `DocumentUploadedEvent` is consumed by `module-ai` after commit for optional extraction

### Remaining

- Bank statement parsing (later banking phases)

### Known Limitations

- Builds and tests were not executed (project restriction)
- Drag-and-drop upload was not added; file picker is the supported path
- Presigned S3 download URLs are not issued; the app streams through the authorized API
- Antivirus scanning is not included
- `DOCUMENT_MAX_FILE_SIZE_MB` is coordinated with Spring multipart (15MB) and Nginx `client_max_body_size 20m`; raising one without the others will still reject large files

### Important Decisions

- Reused `receipts` / `Receipt` instead of creating a parallel Document table
- Upload status is `UPLOADED`; AI disabled still lands on `NEEDS_REVIEW` for manual entry
- Checksum index is non-unique so legitimate duplicate evidence can exist
- Attaching evidence to APPROVED transactions is allowed (does not change amounts) and is audited
- Storage keys never include the user-supplied filename

## Phase 3 — Financial Reporting & Exports

**Status:** complete

Approved income and expense records are aggregated into accountant-usable Profit & Loss, income/expense summaries, period comparison, monthly trends, dashboards, and CSV/XLSX exports. Reporting is read-only and does not mutate ledger data.

### Completed

- SQL aggregations in `ReportingQueryRepository` scoped by `firm_id` + `client_id`; official totals include APPROVED only
- P&L with category breakdown, percentages, `PROFIT` / `LOSS` / `BREAK_EVEN`, and `hasApprovedData`
- Income summary (category + payment method), expense summary (category + largest items)
- Cash Movement Summary explicitly labeled as recorded income minus expenses, not IFRS cash flow
- Auto previous-period and custom period comparison; percent change is `null` when the previous value is 0
- Monthly trend (max 36 months) and top categories
- Client dashboard (approved totals, drafts, document support) and practice workload dashboard (no firm-wide revenue)
- Transaction status counts and document-support metrics from Phase 2 evidence
- Drill-down via existing filtered expense/income APIs (`status`, `categoryId`, `from`, `to`)
- CSV (UTF-8 BOM, escaped) and XLSX (Apache POI numeric money cells) for P&L, income, and expense transactions
- Safe export filenames; export re-checks `requireReportAccess`; `REPORT_EXPORTED` audit (type/period/format only)
- Angular Reports: P&L, Income, Expenses, Trends; owner simplified summary; auditor read-only; `UPLOAD_ONLY` blocked
- Flyway `V18__reporting_approved_view.sql` refreshes `v_approved_transactions` (still excludes DRAFT/VOID)
- Error codes: `INVALID_REPORT_PERIOD`, `REPORT_CLIENT_NOT_FOUND`, `REPORT_ACCESS_DENIED`, `REPORT_EXPORT_FAILED`
- OpenAPI tag/description for report dates, export content types, and role restrictions

### Remaining

- PDF export (optional; not required for V1 accountant workflow)
- Persisted report snapshots (deferred until month-end close needs them)
- OCR / LLM extraction (completed in Phase 4)

### Known Limitations

- Builds and tests were not executed (project restriction)
- The platform is income + expense + categories, not double-entry; there is no Balance Sheet, Trial Balance, General Ledger, or IFRS Cash Flow Statement
- Category hierarchy is metadata only (`parentName`); totals are leaf/category amounts to avoid double-counting
- Excel money cells use POI numeric doubles with `#,##0.00`; presentation scale is 2, `HALF_UP`
- `REPORT_NO_DATA` is unused: empty periods return HTTP 200 with `hasApprovedData=false` rather than an error
- No chart library was added; trends use CSS bars
- PDF is not implemented

### Important Decisions

- Kept the existing client-scoped report URLs (`/api/v1/clients/{clientId}/reports/...`) instead of introducing a second reporting API
- Aggregations run in SQL, not in-memory Java loops over all transactions
- V16 firm/client/status/date indexes are sufficient; no extra index migration
- Currency comes from the firm (`LKR` default); no FX conversion
- Transaction dates (`transaction_date`) define accounting periods
- Practice dashboard counts workload only and never sums client money across businesses

## Phase 4 — OCR & AI-Assisted Document Extraction

**Status:** complete

Uploaded receipts and invoices can be extracted into structured facts and an accounting suggestion. An accountant must accept or modify the suggestion to create a **DRAFT**. Approval remains the existing Phase 1 step. AI is optional.

### Completed

- Provider-agnostic extraction (`DocumentExtractionService`) and separate accounting suggestion (`AccountingSuggestionService`)
- OpenAI-compatible vision/JSON provider, mock filename provider, and disabled/manual fallback
- Async `DocumentUploadedEvent` processing with persisted `PROCESSING` / `FAILED` / `NEEDS_REVIEW` status
- Bank statements and OTHER/BANK_SLIP are not forced through receipt extraction
- Historical vendor/customer category hints before LLM category reasoning
- Category codes/names resolved against the firm's active client-valid categories; LLM UUIDs are ignored
- Amount inconsistency and date sanity flags; field-level confidence stored when the provider supplies it
- Retry with a per-document daily cap; original files are never deleted on failure
- Accept / accept-with-edits / reject-suggestion / manual draft. Accept always creates `DRAFT` + `source=AI`
- Review outcomes `ACCEPTED` / `MODIFIED` / `REJECTED` / `MANUAL`; suggested vs confirmed category retained
- Processing attempts table; token/model metadata when the provider returns usage
- Inbox filters and two-pane review UI with High/Medium/Low confidence wording
- Firm AI toggle plus `GET /api/v1/ai/metrics` for ADMIN
- Flyway `V19__ai_processing_and_review.sql`
- Docs: `docs/AI.md` and `.env.example` placeholders

### Provider Configuration

- `APP_AI_ENABLED=false` and `DOCUMENT_EXTRACTION_PROVIDER=none` — manual review only
- `DOCUMENT_EXTRACTION_PROVIDER=mock` — filename heuristics, no external call
- `DOCUMENT_EXTRACTION_PROVIDER=openai` (or `llm`) with `APP_AI_API_KEY` / `AI_API_KEY` — OpenAI-compatible chat/vision
- `ACCOUNTING_AI_PROVIDER` selects whether a second structured category call is allowed
- Firm setting `aiEnabled` can disable processing for that firm

### Remaining

- AWS Textract (or similar dedicated OCR) is not implemented; no AWS OCR SDK is in the project
- PDF page rasterization / true PDF OCR
- Automatic model retraining from accept/modify diffs
- Bank-statement line extraction

### Known Limitations

- Builds and tests were not executed (project restriction)
- PDF extraction does not render pages; images can be sent to a vision model when size allows
- Mock provider is not OCR
- Confidence is omitted (`Unknown`) when the provider does not supply it
- No Resilience4j circuit breaker; timeouts and a retry cap are used instead

### Important Decisions

- Extended `receipts` AI columns plus `document_processing_attempts` instead of a second document/AI schema
- Prompts are centralized and treat document text as untrusted
- Reporting does not special-case `source=AI` once a transaction is approved

## Phase 5 — Month-End Close & Client Completeness Workflow

**Status:** complete

Accountants can select a client and month, see evidence-driven close readiness, resolve blockers, start review, close, and reopen under audit. Closed periods block financial writes in services. This is a finalized bookkeeping period, not an audited financial statement.

### Completed

- Extended existing `AccountingPeriod` / `document_requests` (no parallel period system)
- Calendar-month create with `startDate`/`endDate`, overlap validation, `reviewStartedAt/By`, `closeNote`
- Lifecycle `OPEN` → `IN_REVIEW` → `CLOSED` → `REOPENED` (ADMIN + reason). `READY` is derived, not persisted
- Extensible `CloseCheck` engine: drafts, document review, unlinked financial documents, open requests, unsupported-approved **warning**
- `BANK_RECONCILIATION_COMPLETE` registered as `DISABLED` (Phase 6/7 hook; unmatched bank lines do not block)
- `GET .../periods/{id}/readiness` with blockers/warnings/checklist/summaries; close re-runs readiness
- Firm work queue `GET /api/v1/close/work-queue` (paginated, assignment-scoped)
- Closed-period guards on expense/income create/update/delete/approve/void (old and new dates) and AI/document draft creation
- Post-close supporting evidence attach allowed (amounts unchanged, audited); unlink of finalized evidence blocked
- Document request `periodId`, owner upload-against-request (`UPLOADED` until accountant completes), cancel without delete
- Angular close dashboard + period workspace; owner/upload-only request upload; P&L/CSV/XLSX via Phase 3 APIs
- Audit: `PERIOD_CREATED`, `PERIOD_REVIEW_STARTED`, `PERIOD_CLOSED`, `PERIOD_REOPENED`, `DOCUMENT_REQUEST_UPLOADED/COMPLETED/CANCELLED`
- Flyway `V20__period_close_completeness.sql`
- Docs: `docs/CLOSE.md`

### Remaining

- Bank statement import and reconciliation as a close check (Phase 6)
- Firm-configurable severity for unsupported transactions (architecture allows it; V1 is warning)
- Period deletion UI (empty OPEN delete was omitted; history is retained)

### Known Limitations

- Builds and tests were not executed (project restriction)
- V1 periods are calendar months; client fiscal-year templates are not configured
- Overlap is enforced in the service (plus year/month uniqueness). No PostgreSQL exclusion constraint (`btree_gist` is not in V1)
- Work-queue status/ready filters are applied after per-client readiness (suitable for typical SME client counts)
- Document date falls back to upload date when no linked transaction or suggested date exists
- Unscoped document requests (no `periodId`) count as blockers for every period of that client until completed or cancelled
- No close snapshot table; live queries over immutable closed data are the V1 source of report values

### Important Decisions

- Reused `accounting_periods` (V11) and `document_requests` (V12) instead of new period/request products
- Bank reconciliation must not block close until it is a real workflow
- 100% readiness means no blockers; warnings can remain at 100%
- Reopen is ADMIN-only in V1
- Attaching evidence after close does not require reopen; changing amounts does

### Future Close Checks

- `BANK_RECONCILIATION_INCOMPLETE` / `BANK_RECONCILIATION_COMPLETE` (Phase 6/7)
- Optional firm policy: unsupported approved transactions as BLOCKER
- Optional evidence-required flag on individual transactions

## Completed

### Phase 1

- Atomic user creation with client assignment validation before persist
- Access types `FULL`, `READ_ONLY`, `UPLOAD_ONLY` enforced and capped by role
- Category uniqueness via `V8` partial indexes
- Client and category get/update/activate/deactivate
- Expense/income void with reason
- User update, activate/deactivate, password change, replace client access
- Structured `errorCode` on ProblemDetail responses

### Phase 2 (earlier foundation, now completed above)

- Receipt-based document upload/download
- Local and S3-compatible `FileStorageService`
- Checksum duplicate detection (`POSSIBLE_DUPLICATE`)
- Document-to-expense/income linking
- `V9` document type and status expansion
- `V17` review metadata, `CREDIT_NOTE`, query indexes

### Phase 3

- Provider-agnostic AI interfaces
- Disabled/manual fallback (`AI_DISABLED`) so the app works without credentials
- OpenAI-compatible adapter
- Accept/modify/reject review creates DRAFT only
- Historical vendor/customer category hints
- `V10` extended extraction fields

### Phase 4 (early P&L foundation; completed and extended as Phase 3 — Financial Reporting & Exports above)

- Real approved-ledger P&L, comparison, dashboard
- CSV and XLSX export

### Phase 5

- Angular 19 + Material workspace in `frontend/`
- Role-based shell, login/register (tokenless register → login), owner and auditor surfaces

### Phase 6

- Accounting periods, readiness blockers, close/reopen, closed-period write guard
- `V11`

### Phase 7

- Document requests and owner upload-against-request
- `V12`

### Phase 8

- CSV bank import, suggested matching, confirm/ignore/missing-receipt
- `V13`

### Phase 9

- Notifications, log/SMTP email abstraction
- Refresh tokens, logout, password reset
- Audit query API and UI
- Firm settings and CORS
- `V14`

### Phase 10

- Manual `FirmSubscription` limits
- `.env.example`, Dockerfiles, Compose, README, roadmap
- `V15`

### Hosting structure

- Gradle modular monolith moved under `backend/`
- Angular remains an independent `frontend/` application (not packaged into the Spring JAR)
- Runtime API base URL via `frontend/src/assets/config.json`
- Same-origin and separate-host Nginx configs in `deploy/nginx/`
- Compose uses service DNS (`postgres`, `backend`) and a document volume
- Production profile requires env secrets; no wildcard CORS with credentials

## In Progress

None.

## Remaining

Phase 6 — Bank Statement Import & Reconciliation. Future work is listed in `docs/PRODUCT_ROADMAP.md`.

## Important Decisions

- Registration remains tokenless; the UI redirects to login.
- Documents reuse the existing `receipts` table.
- AI, S3, and SMTP are optional adapters with safe defaults.
- AUDITOR is always treated as read-only and may see APPROVED and VOID, never DRAFT.
- `UPLOAD_ONLY` is valid only for `BUSINESS_OWNER` and cannot access ledger or reports.
- Defaults: currency `LKR`, timezone `Asia/Colombo`.
- Swagger is enabled for the `local` profile only.

## Known Limitations

- Automated tests and builds were not executed (project restriction).
- SMTP adapter logs intent only; wire a mail sender when credentials exist.
- OpenAI-compatible extraction can send images to a vision model; PDFs are not rasterized in V1.
- SaaS limits are stored but not yet hard-enforced on every write path.
- PDF report generation was intentionally skipped.
