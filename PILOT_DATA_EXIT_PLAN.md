# Pilot data exit plan (90-day pilot)

**Goal:** Customer #1 must not feel trapped. Founder-assisted export is acceptable for pilot.

---

## What the firm can export today (CODE-VERIFIED)

| Data | Method | Role |
|------|--------|------|
| P&L, income, expense detail | CSV/XLSX per client, date range | ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER (own client) |
| Ledger lines | Via expense/income list APIs + exports | Same |
| Documents | Per-document download (`/content`) — authorized | Per RBAC |
| Audit events | Audit UI / API (firm scoped) | ADMIN, AUDITOR |
| Bank transactions | API list endpoints (no bulk export UI dedicated) | ADMIN, ACCOUNTANT |
| Clients / users | List APIs; **no single “export all” button** | ADMIN |

**Gap:** No one-click “firm data package.” **P1** for pilot: documented founder runbook using API + DB backup handoff if contract requires.

---

## Pilot exit procedure (recommended)

### Trigger

- Pilot ends (non-renewal) or customer requests exit within 30 days notice (per pilot agreement).

### Step 1 — Notify & scope (Day 0)

- Confirm which clients and date range to export
- Confirm whether documents must include binary files or metadata only

### Step 2 — Self-serve exports (Day 1–3)

Customer admin (or founder on call):

1. For each client: Reports → P&L / Income / Expenses → CSV or XLSX for each closed month
2. Documents: download from document inbox / client documents (time-consuming for thousands — plan time)
3. Audit: export via audit page screenshots or API pagination (founder script if needed)

### Step 3 — Founder-assisted package (Day 3–7)

If contract requires full package:

1. **Database:** Provide customer a **sanitized pg_dump of firm rows only** OR CSV extracts — requires ops script (not product feature). **LEGAL-REVIEW REQUIRED** for PII handling.
2. **Documents:** Tar of `firms/{firmId}/` from object storage / volume (`deploy/backup/backup-documents-local.sh` pattern)
3. **Manifest:** Spreadsheet listing clients, users (emails), period close dates, export file checksums

### Step 4 — Account wind-down

1. Platform admin: subscription `CANCELLED` (read-only per `SAAS_SUBSCRIPTIONS.md`)
2. Agree retention period (e.g. 30 days) then:
   - Delete firm data via future process **or** manual ops deletion after backup to cold storage
3. Document destruction certificate (LEGAL-REVIEW REQUIRED)

---

## Minimum pilot commitment (contract text suggestion — not legal advice)

- Export assistance included for **90 days** of data in platform
- Documents provided as files customer can open (PDF/JPG/CSV)
- No obligation to provide Xero-format import file (NOT SUPPORTED)

---

## Risks

| Risk | Mitigation |
|------|------------|
| Large document volume | S3 sync or volume tar; quote effort in pilot fee |
| Customer expects Xero re-import | Set expectations in `PILOT_PRODUCT_SCOPE.md` |
| Deleted users/clients | Soft-delete in DB — ops restore from backup if dispute |

---

## P0/P1 classification

| Item | Severity |
|------|----------|
| Ability to export reports CSV/XLSX | **Ready** (CODE-VERIFIED) |
| Written exit procedure | **P1** (this document) |
| Automated firm export API | **P2** |
