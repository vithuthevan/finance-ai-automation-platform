# Demo script — tomorrow

**Golden path:** Seeded **Harbor Ledger Partners** → **Cedar Café (Pvt) Ltd** (document → approve → bank → reconcile → close blocker → close → reports).  
**Duration:** Main flow **~10 minutes**. Short cut **~4 minutes** at end.  
**Login:** `priya@harborledger.demo` / `DemoPass123!`  
**URL:** http://localhost:4200

---

## Suggested opening (natural, not memorized)

> This is a multi-tenant finance operations platform designed for accounting firms managing multiple SME clients. Instead of accountants jumping between documents, spreadsheets, bank statements and separate approval processes, the platform brings the document-to-close workflow into one controlled workspace.

Then sign in and run the steps below.

---

## Main demonstration (~8–12 minutes)

### STEP 1 — Sign in

| | |
|---|---|
| **Screen** | `/login` |
| **Action** | Email `priya@harborledger.demo`, password `DemoPass123!`, **Sign in** |
| **What I say** | “I’m signing in as the firm administrator. Everything below is scoped to our firm; client data is isolated per tenant.” |
| **Expected result** | Redirect to **Dashboard**; no error toast; firm context loaded |

**Status tonight:** PASS (API login verified)

---

### STEP 2 — Firm overview & portfolio

| | |
|---|---|
| **Screen** | `/app/dashboard` |
| **Action** | Point to workflow summary; click **Month-end command center** (or nav **Month-end**). If that page errors, open **Close** instead. Optionally open **My work** (`/app/work`). |
| **What I say** | “Across clients we see what still needs attention for month-end—not just one ledger, but a practice-wide view.” |
| **Expected result** | Multiple clients visible (seed creates **8**); Cedar is the deep-dive client; mixed ready/blocked states on portfolio rows |

**Status tonight:** RISKY on **stale Docker backend** (command center API 404); **PASS** via **Close** / **My work** / **portfolio** APIs on running stack

---

### STEP 3 — Select the demo client

| | |
|---|---|
| **Screen** | Header **Active client** (any page) |
| **Action** | Choose **Cedar Café (Pvt) Ltd** |
| **What I say** | “All documents, transactions, and bank activity are per client—typical firm workflow.” |
| **Expected result** | Cedar selected; banner may show month-end hints |

**Status tonight:** PASS

---

### STEP 4 — Document review → expense draft

| | |
|---|---|
| **Screen** | `/app/documents` → **Review** on Keells receipt |
| **Action** | **Accept as draft** with: Date **5 Sep 2026**, Category **Food & beverage supplies**, Amount **4850**, Party **Keells Super** (AI off = manual fields) |
| **What I say** | “Evidence comes in first; we extract or enter details, then controlled approval posts to the books.” |
| **Expected result** | Draft expense created; success message |

**Status tonight:** PASS (1 document seeded in inbox)

---

### STEP 5 — Approve expenses

| | |
|---|---|
| **Screen** | `/app/expenses` |
| **Action** | Filter **Draft** → **Approve** Keells line |
| **What I say** | “Nothing hits reporting until a human approves—separation of capture and posting.” |
| **Expected result** | Status **APPROVED** |

**Status tonight:** PASS (flow matches Playwright `client-demo-rehearsal.spec.ts`)

---

### STEP 6 — Income & second expense (September books)

| | |
|---|---|
| **Screen** | `/app/income` then `/app/expenses` |
| **Action** | **Income:** 8 Sep 2026, **Café sales**, **18500**, customer “Card settlement”, payment **Card** → Create draft → Approve. **Expense:** 10 Sep 2026, **Utilities**, **6200**, vendor **CEB** → Create draft → Approve. |
| **What I say** | “September activity builds the picture we’ll reconcile to the bank.” |
| **Expected result** | Two more **APPROVED** lines; P&L will show **18,500 / 11,050 / 7,450** after recon |

**Status tonight:** PASS (not pre-seeded for Sept—intentional live entry)

---

### STEP 7 — Bank import & reconciliation

| | |
|---|---|
| **Screen** | `/app/banking` → tab **Import** |
| **Action** | Upload `demo/files/bank-sept.csv` → **Preview** → **Import**. Tab **Reconciliation**: **Confirm** KEELLS, POS SALE, CEB; **Ignore** BANK FEE with reason “Demo bank charge”. |
| **What I say** | “We match bank lines to approved books; unmatched lines and fees are visible before close.” |
| **Expected result** | Reconciliation **100%**; one fee intentionally ignored |

**Status tonight:** PASS (0 bank txns pre-seeded tonight—import is live)

---

### STEP 8 — Close readiness (intentional blocker)

| | |
|---|---|
| **Screen** | `/app/close` → Month **September** 2026 → **Refresh** → **Open workspace** for Cedar |
| **Action** | Show readiness panel: **open document request** blocks **Close period** |
| **What I say** | “Close is gated—we don’t lock the month while evidence or reconciliation is still open.” |
| **Expected result** | **Close period** disabled; blocker mentions document request |

**Status tonight:** PASS (seeded OPEN “September rent invoice”)

---

### STEP 9 — Resolve document request (pick one)

**Option A — Owner story (recommended if time):**

| | |
|---|---|
| **Screen** | Logout → login `amaya@cedarcafe.lk` → **Requested documents** → upload `demo/files/utility-bill.pdf` → Logout → Priya → Close workspace → **Mark complete** |

**Option B — Fast path:**

| | |
|---|---|
| **Screen** | `/app/requests` or period workspace → **Cancel** “September rent invoice” with short note |

| **What I say** | “Client upload is part of the chase workflow; the firm marks evidence complete before close.” |
| **Expected result** | Blocker cleared |

**Status tonight:** PASS (owner has client access via seed)

---

### STEP 10 — Close period

| | |
|---|---|
| **Screen** | Cedar September period workspace |
| **Action** | Optional close note → **Close period** |
| **What I say** | “Closed periods reject new drafts—audit and control.” |
| **Expected result** | Period **CLOSED** |

**Status tonight:** PASS (after blocker cleared)

---

### STEP 11 — Reports (and optional audit)

| | |
|---|---|
| **Screen** | `/app/reports` |
| **Action** | Client Cedar, September 2026, **Run** P&L; mention **Trends** (August seed data) |
| **What I say** | “Reporting reflects approved data only—the same numbers we reconciled.” |
| **Expected result** | Income **18,500**, expenses **11,050**, net **7,450** LKR |

Optional: `/app/audit` — firm-scoped audit trail.

**Status tonight:** PASS (amounts per e2e rehearsal)

---

## Suggested ending

> The goal is not to replace a full accounting ledger. The platform acts as the operational control layer around bookkeeping—helping accounting teams collect evidence, review transactions, reconcile banks, identify blockers and close each client period reliably.

Brief future direction (do **not** demo): Accountant Command Centre depth, AI-assisted recommendations, stronger reconciliation automation, accounting integrations (Xero/Sage/QuickBooks).

---

## Short version (~3–5 minutes)

Use when time is tight. Skip portfolio deep-dive and owner upload.

| Step | Action | Say (one line) |
|------|--------|----------------|
| 1 | Login Priya | Multi-tenant firm workspace |
| 2 | Active client = Cedar | One client’s month-end |
| 3 | Documents → Review Keells → Accept draft → Expenses Approve | Document-to-ledger control |
| 4 | Banking → Import `bank-sept.csv` → Reconcile to 100% | Bank control before close |
| 5 | Close → Sep 2026 → show **blocked** by rent request | Readiness gates |
| 6 | **Cancel** rent request (fast) | Unblock without owner login |
| 7 | **Close period** | Lock month |
| 8 | Reports P&L **7,450** net | Outcome for the client |

---

## Do not demo tomorrow

- From-scratch registration (unless rehearsed 45 min path)
- AR/invoicing depth unless asked
- Platform admin `/platform` unless asked
- Live AI extraction (`APP_AI_ENABLED=false` for demo)
- Unfinished integrations
