# Practice Workflow — Notifications & Work Queues

Phase 7 turns Finance Platform into a daily operating system for accounting firms by surfacing **actionable work** from real accounting events — not generic task management.

## Workflow model

```
ACCOUNTING EVENT → RIGHT PERSON → ACTIONABLE WORK → COMMUNICATION (if needed) → RESOLUTION
```

Work items are **derived** from source-of-truth data (documents, drafts, bank lines, close readiness, document requests). They are not duplicated as a separate task database except for persisted notifications.

## Notification routing

| Event | Recipients | Channel |
|-------|------------|---------|
| Document uploaded | Primary accountant, or assigned accountants | In-app |
| AI processing failed | Assigned accountants | In-app |
| Document request created | Assignee, or business owners / upload users | In-app + email (preference) |
| Requested document uploaded | Requesting accountant | In-app + email |
| Request overdue | Client upload recipients | In-app + email (daily scheduler) |
| Bank import completed | Assigned accountants | In-app |
| Period ready to close | Assigned accountants | In-app + email |
| Period closed | Business owners; auditors (read-only) | In-app + email (owners) |

**ADMIN** is not notified for every receipt upload. **AUDITOR** does not receive draft bookkeeping notifications.

### Primary accountant

`clients.primary_accountant_user_id` routes notifications and workload when set. If unset, all firm accountants with client access receive operational notifications.

Assign via `PUT /api/v1/clients/{clientId}/primary-accountant`.

## Work queue types

| Type | Source | Priority rules |
|------|--------|----------------|
| `DOCUMENT_REVIEW` | Receipts in review statuses | NORMAL |
| `DOCUMENT_PROCESSING_FAILURE` | `FAILED` receipts | HIGH |
| `TRANSACTION_APPROVAL` | DRAFT expenses/income | NORMAL |
| `DOCUMENT_REQUEST` | OPEN/UPLOADED requests | HIGH if overdue |
| `BANK_RECONCILIATION` | UNMATCHED/SUGGESTED/PENDING_APPROVAL | NORMAL |
| `PERIOD_CLOSE` | Current month ready clients | NORMAL |

APIs:

- `GET /api/v1/work/summary` — dashboard counts
- `GET /api/v1/work/my` — paginated work list (ADMIN/ACCOUNTANT)
- `GET /api/v1/work/portfolio` — multi-client workflow view
- `GET /api/v1/work/staff-workload` — ADMIN only

## Document request communication

Structured requests only (no chat). Lifecycle: `OPEN → UPLOADED → COMPLETED` (accountant confirms).

- Client page: `/app/owner` — **Documents needed**
- Manual reminder: `POST /api/v1/clients/{clientId}/document-requests/{id}/remind` (24h cooldown)
- Daily overdue job: `OverdueDocumentRequestScheduler` (dedupe via notification keys)
- Templates: `GET /api/v1/document-request-templates`

## Email

- `APP_EMAIL_PROVIDER=log` — logs content (default, safe for dev)
- `APP_EMAIL_PROVIDER=smtp` — Jakarta Mail SMTP (`MAIL_*` env vars)
- `APP_FRONTEND_BASE_URL` — absolute links in emails

Email failure does **not** roll back accounting transactions. In-app notification is persisted first; delivery status recorded in `notification_deliveries`.

User preferences: `GET/PUT /api/v1/notifications/preferences`

## Security

- Notifications scoped by `firmId` + `userId`; mark-read requires recipient match
- Work queue filtered by role and client assignment
- Activity feed (`GET /api/v1/activity`) reuses audit log with client access rules

## Frontend

- Notification bell in shell (unread badge)
- `/app/notifications` — history
- `/app/work` — My Work (ADMIN/ACCOUNTANT)
- Dashboard cards link to filtered work queues
