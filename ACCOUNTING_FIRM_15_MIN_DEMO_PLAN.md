# Accounting firm 15-minute demo plan

**Audience:** Accounting firm owner, partner, practice manager, or senior accountant  
**Environment:** Seeded **Harbor Ledger Partners** / **Cedar Café** (`demo/seed_demo.py`), **September 2026** (align Close month filter), AI **off**, **Finance Platform** on `http://localhost:4200` or hosted staging  
**Do not show:** Subscription, platform admin, registration, Users client-access gap, trends, AI, multi-module ledger tour  

---

## Narrative arc

**BLOCKED → why → fix evidence → reconcile → request → owner upload → complete → READY → CLOSE → protect → audit**

---

## 0:00–1:00 — Problem + hero view

| Step | Screen | Action |
|------|--------|--------|
| 1 | Login | `priya@harborledger.demo` / `DemoPass123!` |
| 2 | **Month-end close** (`/app/close`) | Set **September 2026**, Refresh |

**Say (outcome-led):**  
“When you’re managing many clients, the question isn’t ‘where’s the document screen?’ — it’s **who can close September, and who’s still blocked?** This is that view for the firm.”

**Bad (feature-led):** “This is our month-end close module with ag-grid.”

**Show:** Cedar Café row — readiness %, blocker count, status **Open** (not ready).

**Do not:** Land on Dashboard first; do not explain charts.

**Objection prep:** “We use Xero” → “We’re not replacing your GL in this pilot — we’re showing **evidence, chase, and close control** on top.”

---

## 1:00–3:00 — Portfolio / client readiness

| Step | Screen | Action |
|------|--------|--------|
| 3 | Same or **My work** | Point at summary cards + portfolio table |
| 4 | Cedar row | **Open workspace** (or navigate from close grid) |

**Say:** “For Cedar Café, the system already knows September isn’t closable — we’ll see exactly **which checks** fail.”

**Business value:** Firm-wide attention without opening five tools.

**If asked multi-client:** “This demo tenant has one client; production pilots start with three to five — the same row appears for each.”

**Recovery:** If period missing, click **Open workspace** — creates period (ADMIN).

---

## 3:00–5:00 — Evidence / document workflow

| Step | Screen | Action |
|------|--------|--------|
| 5 | Period detail **Readiness** | Scroll blockers — point at document review |
| 6 | **Documents** → Keells → **Review** | Accept as draft (date 5 Sep, category Food, 4850) |
| 7 | **Expenses** | Filter Draft → **Approve** |

**Say:** “Evidence becomes an approved record — not a file in a folder.”

**Bad:** “Let me show our documents module.”

**Skip:** Uploading a second Keells copy (already seeded).

---

## 5:00–7:00 — Bank / reconciliation

| Step | Screen | Action |
|------|--------|--------|
| 8 | **Banking** → Import | `demo/files/bank-sept.csv` → Preview → Import |
| 9 | Reconcile tab | Match lines to approved items; **ignore** bank fee if scripted |

**Say:** “Close doesn’t pretend the bank matched — unreconciled lines **block** the period.”

**Do not:** Claim open banking.

---

## 7:00–10:00 — Missing document + owner experience

| Step | Screen | Action |
|------|--------|--------|
| 10 | Period detail | Show **open document request** (rent) as blocker |
| 11 | Logout → Owner login | `amaya@cedarcafe.lk` / `DemoPass123!` |
| 12 | **Owner hub** | Upload `utility-bill.pdf` on open request |

**Say:** “Your client sees **only what you asked for** — not your full ledger.”

**Say:** “Upload isn’t enough — accountant **marks complete** — that’s intentional control.”

**Bad:** Show owner Expenses/Income menus.

**Recovery:** If owner sees empty portal — wrong user or seed failed; re-run seed.

---

## 10:00–12:00 — Blocker resolved → READY → CLOSE

| Step | Screen | Action |
|------|--------|--------|
| 13 | Login Priya again | — |
| 14 | Period detail | **Mark complete** on uploaded request |
| 15 | Refresh readiness | Blockers clear; **Close period** enabled |
| 16 | Close | Optional note → submit |

**WOW moment:** Button was disabled → enabled after real workflow.

**Say:** “September is now **closed** — not a label someone typed.”

---

## 12:00–13:00 — Closed-period control / audit

| Step | Screen | Action |
|------|--------|--------|
| 17 | **Expenses** or **Income** | Attempt create in September → rejection / message |
| 18 | **Audit** (optional 30s) | Filter client — show close action |

**Say:** “If someone tries to change September after close, the system refuses — that’s the control partners care about.”

---

## 13:00–15:00 — Questions / optional report

| If asked | Show |
|----------|------|
| P&L | Period **View P&L** or Reports — 18,500 / 11,050 / 7,450 |
| Export | CSV/XLSX from period |
| Staff access | Mention roles; **do not** open broken Users assignment |
| Data location / exit | `PILOT_DATA_EXIT_PLAN.md` talking points — exports + DB backup on pilot |

**Close CTA:**  
“Let’s pick **three clients**, run **one September close** together with your team — founder-assisted, no annual contract yet.”

---

## What NOT to show (15 min)

- Dashboard plan usage  
- Income creation (beyond what’s needed) if time tight — can pre-approve POS in rehearsal  
- Categories / Users admin  
- Registration  
- AI  
- Trends  
- Auditor login (unless compliance question)  

---

## Likely objections (see `DEMO_OBJECTION_MAP.md`)

- Why not Xero? → Close + evidence layer; OPTION B  
- Double entry? → Pilot scope: cutover clients OR requests-only slice  
- Clients won’t log in? → Show owner upload; offer WhatsApp parallel during pilot  
- Where’s data? → Hosted Postgres + S3/local; export on exit  

---

## Failure recovery

| Failure | Recovery |
|---------|----------|
| 500 on API | Check backend health; restart bootRun |
| Seed stale | `python demo/seed_demo.py` **before** meeting only |
| Wrong app on :4200 | STOP — verify Finance Platform branding |
| Close still blocked | Check open request **COMPLETED**, drafts, bank unmatched |
| Playwright-verified path | Rehearse once; keep `CLIENT_DEMO_CHEAT_SHEET.md` |

---

## Duration / client count / story

| Parameter | Recommendation |
|-----------|----------------|
| Duration | **15 minutes** (this plan) |
| Demo clients visible | **1 live** (Cedar); narrate **3–5** for pilot |
| Story | **Cedar Café September month-end** unblocked live |

---

## Narration contrast summary

| Topic | Bad | Better |
|-------|-----|--------|
| Open | “Accounting platform with modules” | “Who can close September, and why not?” |
| Documents | “DMS” | “Evidence tied to approval and close” |
| Bank | “Recon feature” | “Unmatched bank lines block close” |
| Owner | “Second login product” | “Clients only see what you requested” |
| Close | “Status field” | “Server-enforced readiness — no override” |
