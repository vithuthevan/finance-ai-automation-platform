# Pilot migration plan — Customer #1 (5 SME clients)

**Principle:** Founder-assisted migration is acceptable. **No** unsafe production SQL. **No** dependency on `demo/seed_demo.py` for real firms (CODE-VERIFIED: seed is demo-only).

---

## What can be imported automatically today

| Data | Automatic import | Evidence |
|------|------------------|----------|
| Bank transactions | Yes — CSV per client/account | `GenericBankStatementCsvImporter`, golden path tests |
| Firm / admin | Self-register or founder creates via register API | `RegistrationService` |
| Users | Admin UI create (password set in UI) | `UserService.createUser` |
| Client access | API only | `PUT /api/v1/users/{id}/client-access` |

---

## What is manually configured (Pilot #1)

| Data | Process |
|------|---------|
| Clients | Admin: Clients → Create (one by one) or founder script using public API |
| Categories | Admin: Categories → create set (~15–30 lines); **align names with Xero COA for OPTION A/C** |
| Opening / prior periods | Create accounting period for **cutover month only**; prior months **not** migrated unless firm accepts empty history |
| Bank accounts | UI per client |
| Historical approved transactions | Manual entry, document→draft, or **scope out** (OPTION B) |
| Documents | Owner/accountant upload; optional bulk copy to platform storage via UI |
| Users (staff) | Users UI |
| Business owners | Users UI + **API client-access step** until UI ships |
| Subscription | Platform admin: `extend-trial`, `ACTIVE`, plan upgrade (`SAAS_SUBSCRIPTIONS.md`) |

---

## What should NOT be migrated (pilot)

- Full multi-year GL from Xero
- Payroll history
- Fixed assets / depreciation
- Multi-entity consolidation
- Archived email inboxes as documents (unless firm curates)

---

## Recommended historical scope

| Approach | Start date |
|----------|------------|
| **Minimum (recommended)** | Current calendar month (or month about to close) |
| **Light history** | Last 3 months bank CSV + key invoices only if OPTION A |
| **Maximum (discouraged)** | >6 months re-key — poor ROI for pilot |

---

## Cutover strategy (90-day pilot)

### Week 0 (pre-contract)

- Qualify: CSV banks, English UI, ≤15 clients, no payroll (see readiness report)
- Choose OPTION A, B, or C per `PILOT_LEDGER_COEXISTENCE_STRATEGY.md`
- Legal: pilot agreement + privacy notice (LEGAL-REVIEW REQUIRED)

### Day 1 — Firm shell

1. Firm registers (or founder on Zoom)
2. Firm settings: currency, timezone, **disable AI** for pilot unless agreed
3. Platform ops: set subscription `ACTIVE` or extend trial ≥90 days
4. Create category template (founder: 20–30 categories in 15 min)

### Day 2–3 — Clients

1. Create 5 clients (name, contact email)
2. Create bank account per client (if OPTION A/C)
3. Assign accountants to clients (Users + client access if not ADMIN)

### Day 4–5 — Staff & owners

1. Invite staff (ACCOUNTANT roles + client assignments via API if needed)
2. Create BUSINESS_OWNER per client + `PUT client-access` + smoke-test owner portal
3. Send document request for missing items

### Week 2 — First bank recon

1. Import CSV with saved mapping profile
2. Match / ignore through month-end rules
3. Review readiness blockers

### Month-end — First close

1. Clear drafts, document review, open requests
2. Close period; export P&L CSV/XLSX
3. Compare to Xero if applicable (OPTION C)

---

## Founder validation checklist (per client)

- [ ] Client visible in portfolio
- [ ] Categories exist for all transaction types used
- [ ] Owner can see requests and upload (mobile browser spot-check)
- [ ] Bank import row count ≈ source CSV
- [ ] Reconciliation % acceptable; ignored lines documented
- [ ] P&L approved total spot-checked against sample invoices
- [ ] Close readiness = no blockers (warnings OK)

---

## Tools allowed

- Public REST API with admin JWT
- `demo/verify_demo_state.py` pattern **not** for production data
- Internal scripts calling API (not raw DB) — preferred for bulk client-access

---

## Blockers

| Blocker | Severity |
|---------|----------|
| No client CSV import | P2 — manual OK for 5 clients |
| No Xero import | Expected — use OPTION B/C |
| Owner client-access UI missing | P1 product; **manual API OK for pilot #1** (60-min Zoom) |

**Biggest migration blocker (commercial):** “We already use Xero” — address with coexistence strategy, not engineering.
