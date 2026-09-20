# First customer onboarding runbook

**Scenario:** 1 firm, 2–8 staff, 5 SME clients, founder-led 60–90 min kickoff + async week 1.

---

## Qualification (before contract)

**GOOD PILOT**

- 2–8 staff, 5–15 initial clients
- English UI acceptable; LKR or single currency acceptable
- CSV bank statements
- Simple bookkeeping (no inventory, no payroll in scope)
- Willing to weekly founder calls
- Accepts OPTION A, B, or C from `PILOT_LEDGER_COEXISTENCE_STRATEGY.md`

**BAD PILOT (reject or defer)**

- 100+ clients day one
- Group consolidation / multi-currency
- Regulated bank feed requirement only
- Full GL / payroll / tax filing required
- 24/7 SLA demand
- No willingness to assign client access via founder-assisted step

---

## DAY 0 — Contract

- [ ] Pilot agreement signed (scope, data, AI, exit, support)
- [ ] Privacy notice / DPA draft shared — LEGAL-REVIEW REQUIRED
- [ ] Choose ledger option A/B/C per client
- [ ] Production URL + admin contact confirmed

---

## DAY 1 — Firm setup (Zoom ~60 min)

| Step | Action | Owner |
|------|--------|-------|
| 1 | Register firm at `/login` → Register a firm | Customer admin |
| 2 | Login (no auto-login after register) | Customer |
| 3 | Firm settings: currency, timezone, **turn off AI** unless agreed | Customer admin |
| 4 | Ops: set subscription ACTIVE or extend trial 90d | Founder |
| 5 | Create categories (~20 lines) — use founder template | Founder + customer |
| 6 | Tour: Dashboard → Work queue → Clients | Founder |

**Empty state:** Dashboard shows zeros — **expected** (CODE-VERIFIED). Set expectation: “Next we add clients.”

---

## DAY 2 — Import first clients

- [ ] Create 5 clients (name + email)
- [ ] Optional: bank account per client
- [ ] Assign lead accountant to clients (API client-access if not ADMIN)

---

## DAY 3 — Invite staff

- [ ] Create ACCOUNTANT users
- [ ] Assign client access (API until UI ships)
- [ ] Smoke test: accountant sees only assigned clients

---

## DAY 4 — Business owners

- [ ] Create BUSINESS_OWNER (or UPLOAD_ONLY) per client
- [ ] **PUT `/api/v1/users/{id}/client-access`** — required today
- [ ] Owner login test on phone browser
- [ ] Create document request; owner uploads test file

---

## WEEK 1 — First documents & ledger

- [ ] Upload sample invoices
- [ ] Manual draft → approve (AI off)
- [ ] First expense + income approved
- [ ] P&L smoke test

---

## WEEK 1–2 — First bank reconciliation

- [ ] Import CSV; save mapping profile
- [ ] Match + ignore fees/transfers
- [ ] Work queue bank items → zero unresolved

---

## MONTH END — First close

- [ ] Open period for month
- [ ] Readiness: resolve blockers
- [ ] Close period (ADMIN)
- [ ] Export P&L CSV/XLSX
- [ ] Review audit trail with partner

---

## Success = reach “Aha” fast

**Target Aha (CODE-VERIFIED capability):** Portfolio / work queue shows **N clients, M ready to close, K blocked with reasons** — partner trusts the readiness statement.

Aim to reach first **readiness view with real blockers** by end of week 2.

---

## Manual steps we accept for Pilot #1

- Category template creation
- Client-access API for owners
- Platform subscription activation
- CSV mapping first time per bank
- Legal docs

---

## Training materials

- `docs/USER_GUIDE.md`
- `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md` (internal founder script — adapt names)
- Customer-facing: shorten to 1-page “first week” PDF (P2)
