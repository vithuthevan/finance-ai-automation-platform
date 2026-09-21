# Commercial demo runbook (10–15 minutes)

**Audience:** Accounting firm partner / practice manager  
**Environment:** Local or staging with `python demo/seed_demo.py` completed  
**Logins:** See seed output (`priya@harborledger.demo` / `DemoPass123!`)

---

## Before you start (2 min)

1. Start PostgreSQL, backend (`8080`), frontend (`4200`).  
2. Run: `python demo/seed_demo.py` (idempotent).  
3. Open `http://localhost:4200` — login as **admin**.

---

## Act 1 — The hero (3 min)

**Message:** *“In 60 seconds you see who is ready for month-end and who is blocked — and why.”*

1. Go to **Month-end** (sidebar).  
2. Point at summary: **Ready / Needs attention / Blocked / Closed**.  
3. Use **Focus → Waiting on client** vs **Action required from team** to show accountability.  
4. Scroll **Cedar Café** — blockers grouped under *Waiting on client* / *Your team*; use action buttons.  
5. Mention **Ocean Traders** (draft approval) and **ABC Engineering** (bank import attention) if seeded.  
6. Optional: **Clients → Evidence** on Cedar — monthly checklist and **Create requests for period**.

---

## Act 2 — Operations (3 min)

1. **My work** — overdue requests, approvals, bank recon queue.  
2. **Requests** — firm-wide list; **Send reminder** on overdue OPEN request.  
3. **Users** — show **Access** for business owner (client assignment in UI, no Postman).

---

## Act 3 — Owner + evidence (3 min)

1. Logout → login **owner** (`amaya@cedarcafe.lk`).  
2. Header: *“Your accountant needs N items”* — upload file on open request.  
3. Logout → **admin** → **Requests** or Cedar period — mark upload **complete**.

---

## Act 4 — Close the month (4 min)

**Message:** *BLOCKED → resolve → READY → CLOSE.*

1. **Banking** — import `demo/files/bank-sept.csv` for Cedar if not done; reconcile remaining lines.  
2. **Close** → September 2026 → period workspace — readiness checklist green.  
3. **Close period** → show **CLOSED** on Command Center.  
4. Try a prohibited edit in closed month — show friendly business message (not stack trace).

---

## Act 5 — New firm (optional 2 min)

1. Register new firm in incognito.  
2. Dashboard **onboarding checklist** + **Categories** already populated.  
3. Create first client → Command Center no longer empty.

---

## Closing question for the prospect

*“If this replaced your month-end tracker spreadsheet, what would still be missing for your first five clients?”*

---

## Do not claim in the room

Full GL replacement, Xero sync, bank feeds, tax filing, statutory audit sign-off.
