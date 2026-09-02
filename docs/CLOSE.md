# Month-end close

Month-end close answers: **can this accountant confidently say this client's bookkeeping period is complete?**

It is not a status dropdown. The loop is check → identify missing work → resolve → review → close.

A closed period is a **finalized bookkeeping period**. It is not a legally certified or audited financial statement.

## Accounting periods

Periods belong to a firm and client. V1 creates **calendar months** (`year` + `month` → first/last day). Unique `(firm, client, year, month)` plus service overlap checks on `startDate`/`endDate` prevent overlapping ranges.

Statuses:

- `OPEN` — books can change
- `IN_REVIEW` — an accountant started close review (writes are still allowed)
- `CLOSED` — financial writes for dates in the period are blocked
- `REOPENED` — ADMIN reopened with a reason; writes follow OPEN rules again
- `READY_TO_CLOSE` exists only in the database check for compatibility and is **not** a persisted workflow state. **Ready** in the UI is derived: not closed, and readiness has no blockers.

## Close readiness

`CloseReadinessService` runs pluggable `CloseCheck` implementations. Close **always re-runs** this on the server; the UI cannot bypass it.

### Blockers (prevent close)

- Draft expenses / draft income in the period (`VOID` does not block)
- Documents needing review (`NEEDS_REVIEW`, `PROCESSING`, `EXTRACTED`)
- Failed AI extraction **only if** the document is still unlinked (manual link after failure does not block)
- Unlinked financial documents (`RECEIPT`, `INVOICE`, `PURCHASE_INVOICE`, `SALES_INVOICE`, `CREDIT_NOTE`). `OTHER` is not an automatic blocker
- Open or uploaded (not completed) document requests for the period
- Unmatched, suggested, or pending-approval bank transactions in the period when the client has bank accounts and imported statement data

### Warnings (do not prevent close)

- Approved transactions with no supporting document. Some legitimate entries need no receipt. Later firm policy can raise this to a blocker.
- Bank account configured but no statement imported for the period

### Percentage

`100` if and only if there are no blockers (warnings may still exist).

Otherwise `max(0, 100 - min(90, totalBlockerItemCount * 8))`.

Document accounting date prefers linked transaction date, then AI/manual `suggested_date`, then upload date (last resort; upload time is not an accounting date).

## Closing and reopening

- Close: `ADMIN` or `ACCOUNTANT` with `FULL` access. Optional `closeNote`. Rejects with `PERIOD_NOT_READY_TO_CLOSE` and the current blockers when any blocker remains.
- Reopen: `ADMIN` only, required reason, audit `PERIOD_REOPENED`. Historical close events stay in the audit log.
- After reopen, status is `REOPENED` (writable). Close again when ready.

## Closed-period writes

Enforced in **services**, not only controllers:

- Create / update / delete / approve / void expense and income
- Date changes check both the old and new dates
- AI suggestion accept and document → draft transaction use the same guard
- Linking additional evidence to an already approved transaction **is allowed** if amounts do not change (audited, including `postCloseEvidence`)
- Unlinking evidence from APPROVED/VOID transactions in a closed period is blocked
- Uploading a file is allowed as evidence; it does not silently create a ledger entry in the closed period

Error code: `PERIOD_CLOSED`.

## Document requests

Accountants request missing evidence (`OPEN` → owner upload `UPLOADED` → accountant `COMPLETED`). Cancel keeps history. Open/uploaded requests assigned to the period (or unscoped) are close blockers.

## Reports

Closed periods use the existing Phase 3 P&L and CSV/XLSX exports. Values do not change because the period was closed; they stay stable because writes are blocked.

## Roles

- `ADMIN` / `ACCOUNTANT` — operate close for accessible clients (`ACCOUNTANT` needs `FULL`)
- `AUDITOR` — view periods, readiness, P&L, evidence; cannot review/close/reopen/request
- `BUSINESS_OWNER` — requests and uploads; no close workspace
- `UPLOAD_ONLY` — request upload is a primary action; no ledger/reports/close
