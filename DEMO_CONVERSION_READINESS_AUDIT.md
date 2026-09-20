# Accounting firm demo conversion readiness audit

**Audit type:** Demo conversion (sales narrative, trust, understanding) — not production readiness, not implementation  
**Date:** 2026-09-20  
**Repository verified:** Finance Platform — Angular 20 (`frontend/`), Spring Boot modular monolith (`backend/platform-app`), PostgreSQL + Flyway  
**Business model verified:** Firm → Clients → Users → Documents → Expenses/Income → Approval → Banking → Reconciliation → Requests → Period Close → Reports → Audit  
**Code / DB / production changed during audit:** NO / NO / NO  
**Live browser on localhost:** Not executed — backend and frontend were not running on the audit machine (`/api/v1/health/ready` and `:4200` unreachable). Findings combine **current code**, **e2e `client-demo-rehearsal.spec.ts`**, **operator guides**, and **integration test references** in existing docs.

---

## Executive summary

The product **technically supports** a credible **document-to-close** story for one client (Cedar Café / Harbor Ledger seeded demo): evidence → approve → bank CSV → reconcile → document request → owner upload → mark complete → readiness clears → close → closed-period protection → audit. That path is **UI-complete for the accountant/admin** and **proven in Playwright** when the environment is prepared.

What **weakens conversion** for a 10–15 minute accounting-firm demo is mostly **presentation and narrative**, not missing core engines:

1. **First screen does not lead with month-end portfolio value** — login redirects to **Dashboard** (`app.routes.ts` → `/app/dashboard`), which mixes workflow counts with **per-client P&L-style metrics** and charts. The strongest differentiator screens (**Month-end close** work queue, **My work** portfolio) are **second-class** in navigation and opening flow.
2. **Seeded demo is single-client** — portfolio and “42 clients / 8 blocked” hero story **cannot be shown honestly** without data changes (documented separately in `DEMO_STORY_DATA_RECOMMENDATION.md`).
3. **From-scratch / “I want to try it”** path exposes **empty dashboard**, **no default categories**, and **no Users UI for client assignment** — owner portal looks broken without a **founder API step** (`PUT /api/v1/users/{id}/client-access`).
4. **Commodity ledger screens** (Income, Expenses, P&L) sit **above** Close in the sidebar — easy to accidentally demo “another small accounting package” vs **readiness + evidence workflow**.
5. **Xero coexistence** is the largest **honest** commercial objection; product can answer **partially** today (evidence, requests, close gates, exports) but **not** sync or GL replacement.

**Demo verdict:** **B — CONDITIONALLY DEMO READY** (founder-led, seeded Harbor Ledger, 15-minute cut-down, rehearsed).  
**Paid pilot verdict:** **B — CONDITIONALLY PILOT READY** (aligned with `FIRST_PAID_PILOT_READINESS_REPORT.md` — founder-assisted, ops/legal P0s separate from demo).

---

## Product hypothesis (audit lens)

**Strongest supported value today (code-verified):**  
*An accounting firm can see which clients are ready for the current month-end and what evidence/workflow items block the others* — via `CloseReadinessService`, `PracticeWorkQueueService.portfolio()`, `/api/v1/close/work-queue`, and period detail blocker links.

**Not the primary demo story:** generic document storage, commodity P&L entry, or “we replace Xero.”

---

## Perspective A — Firm owner / partner

| Question | Current answer | Gap severity |
|----------|----------------|--------------|
| See what is happening across the firm? | **Partial** — Dashboard work summary + practice overview; **Close** grid for selected month; **My work** portfolio table | P1 — no firm-level “September: X ready / Y blocked” hero |
| Which clients ready / blocked / why? | **Blocked/ready per client** on Close queue and portfolio **Ready/Blocked**; **why** only on period readiness detail (blocker list with links) | P1 — portfolio rows lack blocker breakdown; not on first screen |
| Which accountant needs attention? | **Backend only** — `GET /api/v1/work/staff-workload` (`WorkController`); **no UI** | P1 conversion / P3 validate demand |
| Trust the status? | **Strong when shown** — close rejects with `PERIOD_NOT_READY_TO_CLOSE`; readiness re-computed server-side | P2 — explain % formula vs binary ready |
| Business value? | Understandable **if narrator leads with close queue**; easy to miss if starting Dashboard P&L section | P1 |

---

## Perspective B — Practice manager

| Question | Current answer |
|----------|----------------|
| Outstanding work? | **My work** queue with typed filters + summary cards |
| Bottlenecks? | Implicit via counts; no SLA / aging dashboard |
| Clients to chase? | Document request items + overdue counts |
| Waiting approval / recon? | Summary cards + queue types `TRANSACTION_APPROVAL`, `BANK_RECONCILIATION` |
| **Friction** | Portfolio table **not clickable**; must use queue **Open** or Close grid |

---

## Perspective C — Accountant / bookkeeper

| Question | Current answer |
|----------|----------------|
| What next? | **My work** — prioritized list with `actionUrl` navigation |
| Clicks? | Golden path is **moderate** (document review → expenses approve → income create → banking import → close) — e2e timeout 300s for full path |
| Double entry? | **Risk** if prospect uses Xero — must position OPTION B or cutover-only OPTION A |
| vs Excel/WhatsApp? | Requests + owner portal **competitive** for chase; ledger entry **not** faster than Excel for power users |

---

## Perspective D — Business owner / SME client

| Question | Current answer |
|----------|----------------|
| What does accountant need? | **Owner hub** — document requests prominent, step UI |
| Upload easily? | **Yes** — dropzone + upload on request card |
| Outstanding clear? | **Yes** when requests exist; empty state is calm |
| Wrong client access? | **Backend enforced** — empty if no assignment |
| Phone browser? | Responsive patterns exist; not validated on devices in this audit |
| vs WhatsApp? | **Still easier** for one-off photos without login — adoption risk |

**Owner portal friction:** assignment requires **API** today; nav still exposes **Expenses/Income/Reports** for BUSINESS_OWNER (accounting-package feel).

---

## Perspective E — Auditor / reviewer

| Question | Current answer |
|----------|----------------|
| Trace actions? | **Audit** page (ADMIN/AUDITOR) |
| Evidence? | Documents + linked transactions |
| Period locking? | Demonstrable on close + write rejection (golden path) |
| Read-only role? | AUDITOR role in nav (no admin mutations) — **show only if asked** |

---

## Journey A — Seeded sales demo (Harbor Ledger / Cedar Café)

**Prepared flow exists:** `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md`, `demo/seed_demo.py`, `e2e/tests/client-demo-rehearsal.spec.ts`.

| Stage | Supported? | Demo friction |
|-------|------------|---------------|
| Login | Yes | Must confirm **Finance Platform** on :4200 |
| Portfolio/dashboard | Partial | Dashboard first — **not** multi-client hero |
| Document review | Yes | Keells pre-seeded NEEDS_REVIEW |
| Approve / income / expenses | Yes | Multiple screens — **Xero-like** |
| Bank import | Yes | CSV not imported until live step |
| Reconciliation | Yes | 4 lines — manageable |
| Open rent request | Yes | Pre-seeded OPEN |
| Owner upload | Yes | utility-bill.pdf stand-in for rent |
| Mark complete | Yes | UPLOADED blocks until complete — **say this** |
| Close / protection / audit | Yes | Strong trust moments |
| Reports | Yes | P&L — **optional** in 15 min |

**Suitable for 10–15 min?** **PARTIAL** — cut-down in operator guide exists; narrator must **skip** dashboard charts, subscription, plan usage, trends, full income/expense tour.

**Strongest moment today:** **Close workspace** blocker list → resolve last blocker → **Close period** enabled → server accepts → optional write rejection after close.

**Weakest moment today:** **First 60 seconds on Dashboard** — single client, P&L-empty message, feature cards without “blocked because…” story.

---

## Journey B — From scratch (prospect says “I want to try it”)

Verified in `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md` + code:

| Step | UI without founder? |
|------|---------------------|
| Register firm | Yes |
| Login | Yes (no auto-login after register) |
| First dashboard | Empty zeros — **weak value** |
| Create client | Yes |
| Categories | **Manual** — empty forms until created |
| Create staff/owner | Yes |
| Assign client to owner | **NO UI** — API only |
| Rest of golden path | Yes after setup |

**60-second post-login for new firm:** **NO** — does not communicate readiness portfolio.

---

## Hero screen analysis

| Screen | Business question | Fit as HERO |
|--------|-----------------|-------------|
| **Dashboard** | “What needs attention today?” (partial) | **Current default** — mixes practice + **client P&L** |
| **My work** | “What should I do next?” + portfolio table | Good for **accountant**; weak blocker detail |
| **Month-end close** (`/app/close`) | “Who can close this month and how many blockers?” | **Best existing HERO** for partner story |
| Period detail | “Exactly what blocks Cedar Café September?” | **Best WOW** resolution screen |

**Recommended hero (no build required for demo):** Start demo on **Month-end close** (September 2026) → one row → **Open workspace** — then narrate backward to evidence/recon as needed.

**Ideal hero (product gap):** Firm month-end banner with ready/blocked/attention counts + per-client blocker bullets — **mostly UI**; backend data largely exists (`portfolio`, `work-queue`, readiness APIs).

---

## Screen → business question map

| Screen | Question answered | Weak / missing question |
|--------|-------------------|-------------------------|
| Dashboard | Mixed attention + client month snapshot | “Which clients blocked for month-end?” |
| My work | What to do next | “Why is client blocked?” (portfolio) |
| Clients | Who we serve | Readiness not shown |
| Documents | What evidence arrived | — |
| Expenses/Income | Record transactions | **Commodity** — triggers Xero comparison |
| Banking | What needs reconciliation | — |
| Close | Month-end control | — |
| Reports | Financial outcomes | Commodity |
| Audit | Who did what | — |
| Users | Team admin | **Client access** missing |
| Subscription | SaaS limits | **Do not show** in sales demo |

---

## Demo friction log (presenter apologies)

| Moment | Class | Notes |
|--------|-------|-------|
| “Ignore plan usage / subscription” | MEDIUM | ADMIN dashboard |
| “Only one client in portfolio” | HIGH | Seed design |
| “Rent invoice is this utility PDF” | MEDIUM | Documented stand-in |
| “Owner assignment via API” (from-scratch) | CRITICAL if shown | Hide from sales demo |
| “We enter transactions here — Xero may still be SOR” | HIGH | Positioning |
| “Refresh if list stale” | LOW | Generally reactive loads |
| “Development on localhost” | MEDIUM | Use hosted staging for serious prospects |
| “Mark complete after owner upload” | HIGH | Must explain or close stays blocked |
| “Bank fee line — ignore or match” | LOW | Training moment |
| AI extraction off / manual draft | LOW | Correct for demo |

---

## UI / UX / terminology (audit only)

| Issue | Examples | Class |
|-------|----------|-------|
| Internal nav label “Document to Close” | Sidebar brand | LOW — good internally |
| “Readiness %” | Close grid | MEDIUM — partners want ready/blocked |
| “My work” vs “Practice portfolio” | Split across pages | P1 |
| BUSINESS_OWNER sees full ledger nav | shell `flatItems()` | P1 owner adoption |
| Users subtitle “manage their access” | **Misleading** — no client access UI | P1 trust |
| Status badges using wrong semantic colors | Users role badge `PENDING` | P2 polish |

---

## Demo data realism

Harbor Ledger + Cedar Café: **believable** names, LKR amounts, Sri Lanka context. **Single client** undermines “firm managing dozens.” August history vs September live demo: **coherent** if narrator explains. **Fake rent PDF** — acceptable if disclosed lightly.

**One story?** **Yes** for seeded path if presenter **does not** tour every nav item.

---

## AI in demo

| Check | Result |
|-------|--------|
| Configured | Firm `aiEnabled`; env `APP_AI_ENABLED` |
| Demo seed | AI **off** |
| Reliable live? | **Risk** — PDF not true OCR (`PILOT_PRODUCT_SCOPE.md`) |
| Saves visible time? | **Unclear** in 15 min |
| Distraction? | **Yes** if enabled |

**Recommendation:** **DEMO OFF**

---

## Reporting in 15-minute demo

| Report | Show |
|--------|------|
| Period readiness / close summary | **CORE** |
| P&L (brief, post-close) | **OPTIONAL IF ASKED** |
| Trends | **DO NOT SHOW** (dull with one month) |
| Income/expense drill reports | **DO NOT SHOW** first demo |
| Excel/CSV export | **OPTIONAL** — trust / exit narrative |

---

## Trust signals to surface in demo

- Role separation (switch to owner / mention AUDITOR read-only)
- Client scoping (active client header)
- Close blocker enforcement + reopen ADMIN-only
- Audit log entry on close
- Approved-only P&L (if shown)

---

## Could founder demo tomorrow?

| Criterion | Answer |
|-----------|--------|
| Without embarrassment (seeded, rehearsed) | **Yes** |
| Without Postman | **Yes** for Journey A; **No** for pure UI owner onboarding |
| Without terminal during presentation | **No** — prep requires docker/seed/start (acceptable **before** meeting) |
| Without explaining unfinished behaviour | **Partial** — rent PDF, single client, Xero positioning |
| Recover if workflow fails | **Partial** — reset via `seed_demo.py` |
| Firm owner value in 3 minutes | **Partial** — only if open **Close** not Dashboard |
| Accountant daily workflow | **Yes** via My work |
| Owner understands task | **Yes** after assignment |

---

## Features / screens NOT in first sales demo

- Subscription / plan usage charts  
- Platform admin (`/platform`)  
- Categories admin (unless from-scratch)  
- Firm settings deep dive  
- Trends report  
- Long expense/income module tour  
- AI extraction UI  
- Auditor role (unless trust question)  
- Registration flow (unless “self-serve” question)  
- Users page client-access gap (never show broken owner)  
- Income **and** expense **creation** tour — do minimum to unblock close  

---

## Verdicts

### Demo readiness: **B — CONDITIONALLY DEMO READY**

Conditions: seeded DB, September month context, presenter opens **Month-end close**, 15-minute script, honest Xero coexistence talk, AI off, do not show from-scratch onboarding.

Not **D — HIGH-CONFIDENCE SALES DEMO** until: multi-client story data, hero landing, owner assignment UI or pre-seeded owner, hosted non-local URL.

### Paid pilot readiness: **B — CONDITIONALLY PILOT READY**

Product core sufficient for founder-assisted pilot; ops/legal/backup/SMTP separate. Demo polish ≠ pilot safety.

---

## Related deliverables

- `DEMO_CONVERSION_GAP_MATRIX.md`
- `ACCOUNTING_FIRM_15_MIN_DEMO_PLAN.md`
- `DEMO_OBJECTION_MAP.md`
- `SPRINT_1_DEMO_CONVERSION_PLAN.md`
- `DEMO_STORY_DATA_RECOMMENDATION.md`

---

## Evidence sources

- `frontend/src/app/app.routes.ts`, `dashboard.page.ts`, `work.page.ts`, `close.page.ts`, `period-detail.page.ts`, `users.page.ts`, `shell.component.ts`, `owner.page.ts`
- `backend/.../PracticeWorkQueueService.java`, `CloseReadinessService.java`, `WorkController.java`
- `demo/seed_demo.py`, `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md`, `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md`
- `e2e/tests/client-demo-rehearsal.spec.ts`
- `FIRST_PAID_PILOT_READINESS_REPORT.md`, `PILOT_PRODUCT_SCOPE.md`, `PILOT_LEDGER_COEXISTENCE_STRATEGY.md`
