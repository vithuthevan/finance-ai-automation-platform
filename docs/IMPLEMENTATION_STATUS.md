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
- `DocumentUploadedEvent` still emitted for a future AI subscriber; processor does not mark `PROCESSING` when AI is disabled

### Remaining

- OCR / LLM extraction and suggestion UI (Phase 4 AI)
- Bank statement parsing (later banking phases)
- Reporting polish that consumes document links (Phase 3)

### Known Limitations

- Builds and tests were not executed (project restriction)
- Drag-and-drop upload was not added; file picker is the supported path
- Presigned S3 download URLs are not issued; the app streams through the authorized API
- Antivirus scanning is not included
- `DOCUMENT_MAX_FILE_SIZE_MB` is coordinated with Spring multipart (15MB) and Nginx `client_max_body_size 20m`; raising one without the others will still reject large files

### Important Decisions

- Reused `receipts` / `Receipt` instead of creating a parallel Document table
- Initial post-upload status is `NEEDS_REVIEW` so the inbox works with AI disabled
- Checksum index is non-unique so legitimate duplicate evidence can exist
- Attaching evidence to APPROVED transactions is allowed (does not change amounts) and is audited
- Storage keys never include the user-supplied filename

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

### Phase 4

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

Phase 3 — Financial Reporting & Exports. Future work is listed in `docs/PRODUCT_ROADMAP.md`.

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
- OpenAI adapter uses filename/metadata extraction, not full vision OCR, unless a provider is configured.
- SaaS limits are stored but not yet hard-enforced on every write path.
- PDF report generation was intentionally skipped.
