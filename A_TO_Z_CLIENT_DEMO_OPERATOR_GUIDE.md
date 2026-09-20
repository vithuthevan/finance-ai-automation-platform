# A-TO-Z CLIENT DEMO OPERATOR GUIDE

Definitive presenter script for **Finance Platform** (Angular + Spring Boot + PostgreSQL).

Another person who can use a browser should be able to run this demo without asking what to click.

**Companion files**

- `CLIENT_DEMO_CHEAT_SHEET.md` — one-page sequence
- `CLIENT_DEMO_DATA_CARD.md` — credentials and numbers

**Target duration:** 28–32 minutes (full) · 15 minutes (cut-down at the end)

**Do not** change application code, deploy, or rehearse the mutating workflow after the final reset.

---

## REPOSITORY CHECK

This repository **is** Finance Platform.

| Layer | Evidence |
|---|---|
| Product | Finance Platform — Document to Close |
| Frontend | Angular 20 (`frontend/`, `finance-platform-web`) |
| Backend | Spring Boot modular monolith (`backend/platform-app`) |
| Database | PostgreSQL (`finance_platform`) |
| Hierarchy | Accounting firm → Clients → Users |

If the Sign in card does **not** say **Finance Platform**, you are in the wrong application. **STOP.**

Named runbooks requested in the brief were **not present** in this repo:

- `FINAL_CLIENT_DEMO_RUNBOOK.md` — not found
- `FULL_CLIENT_DEMO_REHEARSAL.md` — not found
- `FINAL_BROWSER_DEMO_REHEARSAL.md` — not found
- `FINAL_UI_GOLDEN_PATH_VERIFICATION.md` — not found
- `FINAL_UI_UX_REDESIGN_REPORT.md` — not found

This guide is built from **current** `demo/seed_demo.py`, `demo/README.md`, `e2e/tests/client-demo-rehearsal.spec.ts`, and live Angular templates. Where those sources disagree, **current UI/code wins**.

Browser execution of the golden path was **not** run while writing this document, so a prepared demo database would not be consumed. Items that still need a 30-second look on a spare machine are marked **VERIFY BEFORE DEMO**.

---

## DEMO DATA REFERENCE

Verified from `demo/seed_demo.py` (not guessed).

### Firm

| Field | Value |
|---|---|
| Name | Harbor Ledger Partners |
| Registration no. | PV-DEMO-001 |
| Currency | LKR |
| Timezone | Asia/Colombo |
| Financial year start | April |
| AI extraction | **Off** |

### Client

| Field | Value |
|---|---|
| Name | Cedar Café (Pvt) Ltd |
| Business reg. no. | PV0023456 |
| Contact email | accounts@cedarcafe.lk |

### Users (same intentional demo password)

Password for every seeded demo user: **`DemoPass123!`**  
Source: `demo/seed_demo.py` → `PASSWORD`. Also `e2e/support/constants.ts`.  
These are **local demo credentials only**.

| Name | Role | Email | Used in main demo? |
|---|---|---|---|
| Priya Fernando | ADMIN | `priya@harborledger.demo` | **YES** |
| Nimal Perera | ACCOUNTANT | `nimal@harborledger.demo` | **NO** (optional only) |
| Amaya Silva | BUSINESS_OWNER | `amaya@cedarcafe.lk` | **YES** |
| Ravi Jay | AUDITOR | `ravi@harborledger.demo` | **NO** (only if seeded with `--with-auditor`; Starter plan max 3 users) |

Nimal is set as primary accountant. Priya (ADMIN) can still run the entire live path.

### Bank

| Field | Value |
|---|---|
| Bank | Commercial Bank |
| Account name | Cedar Café Operating |
| Masked number | ****4521 |
| Currency | LKR |
| Import dropdown | **Commercial Bank · Cedar Café Operating** |

### Categories

| Code | Name | Type |
|---|---|---|
| EXP-FOOD | Food & beverage supplies | EXPENSE |
| EXP-UTIL | Utilities | EXPENSE |
| EXP-BANK | Bank charges | EXPENSE |
| EXP-RENT | Rent | EXPENSE |
| INC-SALES | Café sales | INCOME |

### What is already in the database after a clean seed

| Item | State | Your job |
|---|---|---|
| Keells receipt `keells-receipt.pdf` | Documents inbox, **NEEDS_REVIEW** | Review live — **do not upload another copy** |
| September rent invoice request | **OPEN** | Owner uploads live |
| Bank CSV | **Not imported** | Import live |
| September Keells / POS / CEB ledger | **Not created** | Create and approve live |
| August history | Approved expense 9,200 + income 45,000 (Trends) | Do not use these as September P&L |
| September period | Open / not closed | Close live |

### September figures you will create live

| Date | Record | Amount |
|---|---|---|
| 2026-09-05 | Expense · Keells Super · Food & beverage supplies | 4,850 |
| 2026-09-08 | Income · Card settlement · Café sales · Card | 18,500 |
| 2026-09-10 | Expense · CEB · Utilities | 6,200 |

**Expected September P&L (approved only)**

- Total Income: **18,500.00**
- Total Expenses: **11,050.00**
- NET PROFIT: **7,450.00**

Browser locale may show `18,500.00`, `18500.00`, or `LKR 18,500.00`. Match the **numbers**.

### Demo files

| Exact path | When to use | What it represents |
|---|---|---|
| `demo/files/keells-receipt.pdf` | Already seeded in inbox | Keells Super supplies receipt (5 Sep) |
| `demo/files/keells-receipt.png` | Only if the PDF row is missing | Same receipt as an image |
| `demo/files/bank-sept.csv` | Banking → Import | Commercial Bank September statement (4 lines) |
| `demo/files/utility-bill.pdf` | Amaya rent-request upload | **Stand-in rent invoice.** There is no dedicated rent PDF in `demo/files/`. Current demo README and e2e both use this file. **Do not invent another filename.** |

Absolute folder on this machine:

`C:\Users\HP\Downloads\finance-ai-automation-platform\demo\files\`

### Bank CSV contents (`demo/files/bank-sept.csv`)

```
Date,Description,Reference,Debit,Credit,Balance
2026-09-05,KEELLS SUPER KANDY,REF1001,4850.00,,125150.00
2026-09-08,POS SALE CARD,INV2044,,18500.00,143650.00
2026-09-10,CEB ELECTRICITY,UTIL778,6200.00,,137450.00
2026-09-12,BANK FEE,FEE01,250.00,,137200.00
```

---

## PORTS AND WRONG-APP WARNING

| Service | Port |
|---|---|
| Finance Angular (`npm start`) | **4200** |
| Finance backend | **8080** |
| PostgreSQL | **5432** |

**Current application URL:** http://localhost:4200  
**Login URL:** http://localhost:4200/login

⚠ **DO NOT OPEN http://localhost:4201**  
This Finance Platform default is **4200**, not 4201. Port 4201 is not configured in `frontend/package.json` or `angular.json`. If you force 4201 without updating CORS (`APP_CORS_ALLOWED_ORIGINS`), Sign in will fail.

⚠ **CONFIRM THE SIGN IN CARD SAYS “Finance Platform”**  
Other products (including CareHome-style apps) also commonly use port **4200**. If you see a different product name, you are in the wrong repository/process. **STOP.**

---

## BEFORE THE CLIENT ARRIVES

Do this once. Then **stop**. Do not click through Documents, Banking, or Close after the final reset.

### What you must NOT do before the client sits down

- Do **not** import `bank-sept.csv`
- Do **not** Accept / Approve Keells
- Do **not** create the POS 18,500 or CEB 6,200
- Do **not** upload the rent request as Amaya
- Do **not** close September
- Do **not** rehearse the workflow after the final reset
- Do **not** run `python demo/seed_demo.py` again mid-demo
- Do **not** open CareHome or any non-Finance UI

### Directory

Repository root:

`C:\Users\HP\Downloads\finance-ai-automation-platform`

---

### STEP 0 — Confirm you are in Finance Platform

**SCREEN:** This folder, not another product repo.

**VERIFY:**

- Folder name `finance-ai-automation-platform`
- `frontend/package.json` name is `finance-platform-web`
- `demo/seed_demo.py` contains `Harbor Ledger Partners`

**DO NOT:** Continue if this is CareHome, Temple ERP, Omkaarya, Hytec, or another product.

---

### STEP 1 — Start PostgreSQL

**RUN FROM:** repository root  
**TERMINAL:** 1

```powershell
docker compose up -d postgres
```

**VERIFY:** Container is healthy.

**VERIFY BEFORE DEMO — database password must match the backend**

- Compose default password is `change-me` unless `.env` sets `POSTGRES_PASSWORD`
- Local Spring profile default is `postgres` (`application-local.yml`)
- `demo/README.md` expects you to set `POSTGRES_PASSWORD=postgres` in `.env` when using local-profile defaults

If backend cannot connect, align the two passwords. Do not guess production secrets.

---

### STEP 2 — Start the backend

**RUN FROM:** `backend`  
**TERMINAL:** 2

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/finance_platform"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:4200"
$env:APP_AUTH_COOKIE_SECURE="false"
$env:APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL="priya@harborledger.demo"
$env:APP_AI_ENABLED="false"
$env:APP_EMAIL_PROVIDER="log"
.\gradlew.bat :platform-app:bootRun
```

`application-local.yml` already supplies a **non-production** JWT default. You do not need to paste a real secret on a slide.

**VERIFY:** Log shows the app started. Then open:

http://localhost:8080/api/v1/health/ready

**EXPECTED RESULT:** HTTP 200 (body is a readiness payload, not a login page).

**IF THIS FAILS:** Backend is not up. Do not open the frontend yet.

---

### STEP 3 — Start the frontend

**RUN FROM:** `frontend`  
**TERMINAL:** 3

First time on this machine only:

```powershell
cd frontend
npm install
npm start
```

Already installed:

```powershell
cd frontend
npm start
```

**EXPECTED:** Dev server on **http://localhost:4200** (proxies `/api` to `http://localhost:8080`).

**DO NOT:** `npm start -- --port 4201`

---

### STEP 4 — Seed demo data

**RUN FROM:** repository root  
**TERMINAL:** 4  
**WHEN:** Backend is ready. Frontend may already be running.

```powershell
python demo/seed_demo.py
```

**EXPECTED (console includes):**

- `DEMO READY`
- `ADMIN        priya@harborledger.demo / DemoPass123!`
- `OWNER        amaya@cedarcafe.lk / DemoPass123!`
- Firm Harbor Ledger Partners
- Client Cedar Café (Pvt) Ltd

The seed script currently prints `open http://localhost:4200`. That port is correct.

**DO NOT:** Pass `--skip-document` (you need the Keells inbox row).  
**DO NOT:** Pass `--with-auditor` (Starter plan is 3 users).

**IF THIS FAILS:** Fix backend connectivity first. Do not keep re-running seed against a half-started API.

---

### STEP 5 — Non-destructive UI check, then STOP

**OPEN:** http://localhost:4200/login

**SCREEN:** Sign in

**VERIFY:**

- Brand: **Finance Platform**
- Heading: **Sign in**
- Fields: **Email**, **Password**
- Button: **Sign in**
- Link: **Register a firm**

**DO NOT CLICK:** Sign in, Register a firm, or any demo workflow.

**STOP.** Leave the browser on this page or close it. Wait for the client.

**IF THIS FAILS:** You are not on Finance Platform, or the frontend is not the process on 4200.

---

## OPENING STATEMENT (~30–45 seconds)

Say this before you type. Do not read it like a script; keep it natural.

> “I’ll walk you through the full bookkeeping cycle we run for a client firm. We’ll collect the evidence, turn it into an accounting record, review and approve it, match it to the bank, close the month under control, and then look at the reports and the audit trail. One connected workflow: collect, review, approve, reconcile, close, report.”

Then start STEP 6.

---

# FULL DEMO — NUMBERED STEPS

**Total target: 28–32 minutes**

---

## A. Login and workspace (~1 min)

### STEP 6 — Open the application

**SCREEN:** Browser

**OPEN:** http://localhost:4200/login

**EXPECTED RESULT:** Sign in card, brand **Finance Platform**.

**DO NOT:** Open `http://localhost:4200` if the card is a different product.  
**DO NOT:** Open `http://localhost:4201`.

**IF THIS FAILS:** Confirm `npm start` is the Finance frontend.

---

### STEP 7 — Sign in as firm administrator

**SCREEN:** Sign in

**ENTER:**

- Email: `priya@harborledger.demo`
- Password: `DemoPass123!`

**CLICK:** **Sign in**

**EXPECTED RESULT:** You leave `/login` and land on **Dashboard** (`/app/dashboard`).

**VERIFY:**

- Top bar name: **Priya Fernando**
- Top bar role: **Admin**
- Initials: **PF**
- Sidebar brand: **Finance Platform** / **Document to Close**
- Page title: **Dashboard**
- Breadcrumb: **Practice workspace › Firm workspace**

**SAY TO CLIENT:**  
“This is the accounting firm workspace. Priya is Harbor Ledger’s administrator.”

**DO NOT:** Click **Register a firm**.  
**DO NOT:** Click **Platform admin** if it appears (Priya can be a platform admin when bootstrap email is set).

**IF THIS FAILS:** Recheck caps and the `!` in the password. Confirm seed succeeded. Do not try production emails.

---

## B. Dashboard and client (~2 min)

### STEP 8 — Orient on Dashboard (point only)

**SCREEN:** Dashboard

**POINT AT (do not click through every queue):**

| Control | Why |
|---|---|
| **Documents to review** | Inbox work waiting |
| **Approvals pending** | Draft ledger items |
| **Today’s workflow** cards | Bank unresolved, overdue requests, clients ready to close |
| **Practice overview** | Active clients (expect **1**) |
| **Selected client this month** | Cedar Café snapshot |

**VERIFY (typical clean seed, September 2026):**

- Documents to review: **1** (Keells)
- Selected client **Income / Expenses** for *this month*: still empty or near-empty of *September approved* activity (August is a prior month)
- You may see **No approved activity this month for the selected client.** That is correct before you approve September items.

**DO NOT:** Spend time explaining Plan usage, charts, or Subscription.

**SAY TO CLIENT:**  
“This is the morning picture for the practice — what needs review, what needs approval, and whether a client is ready to close.”

---

### STEP 9 — Confirm Cedar Café is the active client

**SCREEN:** Dashboard

**LOOK:** Top bar, right side.

- If only one client is seeded, the header shows the name **Cedar Café (Pvt) Ltd** (not a dropdown).
- If a dropdown labelled **Active client** is present, open it and select **Cedar Café (Pvt) Ltd**.

**ALSO:** In **Selected client this month**, the **Client** dropdown:

**SELECT:** **Cedar Café (Pvt) Ltd**

**VERIFY:** The selected-client cards refresh for Cedar Café.

**SAY TO CLIENT:**  
“One accounting firm can manage many SME clients. Every document, expense, bank line, and report is scoped to the client you have open — records do not mix.”

**DO NOT:** Create another client.  
**DO NOT:** Click **Deactivate** anywhere.

**IF THIS FAILS:** Open left nav **Clients** and confirm the row **Cedar Café (Pvt) Ltd** / **Active** exists, then return to Dashboard. Do not create a duplicate client.

---

## C. Evidence → accounting record → approval (~7–8 min)

### STEP 10 — Open Documents

**SCREEN:** Any firm page

**CLICK:** Left navigation **Documents** (group **Operations**)

**EXPECTED RESULT:** URL `/app/documents`. Title **Document inbox**.

**VERIFY:**

- Status filter is already **Needs review** (ADMIN default)
- Upload panel is visible — **you will not use it**

**DO NOT:** Click **Upload**.  
**DO NOT:** Change Document type and add a second Keells file.

---

### STEP 11 — Locate the seeded Keells document

**SCREEN:** Document inbox

**IF NEEDED:**

- Client: **All assigned** is fine (only Cedar Café exists)
- Status: **Needs review**
- Type: **All**

**FIND THE ROW:**

| Column | Expect |
|---|---|
| Document | **keells-receipt.pdf** |
| Client | Cedar Café (Pvt) Ltd |
| Type | RECEIPT |
| Status | **NEEDS_REVIEW** |

**CLICK:** **Review** on that row  
(or click the filename **keells-receipt.pdf**)

**EXPECTED RESULT:** **Document review** (`/app/documents/:clientId/:documentId`)

**SAY TO CLIENT:**  
“The source file is already in the inbox. We are not uploading a second copy — we are turning this evidence into a bookkeeping record.”

**IF THIS FAILS:** Set Status to **All**. Look for `keells-receipt.pdf`. If it is truly missing, **then** use the upload panel: Client **Cedar Café (Pvt) Ltd**, Document type **Receipt**, Note `Keells supplies 5 Sep`, **Choose file** → `demo/files/keells-receipt.pdf`, **Upload**. Only do this if the seeded row is absent.

---

### STEP 12 — Point at the review workspace

**SCREEN:** Document review

**POINT AT (these sections exist):**

1. **Source document** (left) — PDF preview of the receipt  
2. File meta — `keells-receipt.pdf` · Cedar Café · RECEIPT · status **Needs review**  
3. **Extracted information** — with AI off, values will likely be **—**  
4. Accounting form — heading **Accounting suggestion** *or* **Enter transaction manually**  
5. You may see: **AI extraction is disabled. Enter the transaction manually.** That is expected for this demo.

**DO NOT DESCRIBE:** Controls that are not on the screen.  
**DO NOT CLICK:** Download, Retry processing, Reject suggestion, Link expense, Link income, Reject document.

**SAY TO CLIENT:**  
“Left side is the evidence. Right side is what we post to the books. Extraction is optional — the accountant still confirms the numbers.”

---

### STEP 13 — Complete every accounting field

**SCREEN:** Document review → accounting form

**SELECT / ENTER:**

| Field | Action | Value |
|---|---|---|
| Type | Leave or select | **Expense** |
| Date | Open the calendar icon | **September 5, 2026** |
| Category | Open dropdown | **EXP-FOOD Food & beverage supplies** |
| Amount | Type | **4850** |
| Tax | Leave default | **0** |
| Currency | Leave default | **LKR** |
| Party | Type | **Keells Super** |
| Reference no. | Leave blank | — |
| Description | Leave blank | — |

Payment method is **not shown** for Expense. Do not look for it here.

**VERIFY BEFORE DEMO:** Date typing depends on Windows locale (`9/5/2026` vs `5/9/2026`). Prefer the **calendar**, not typed text.

**CLICK:** **Accept as draft**

**DO NOT CLICK:** **Reject suggestion** · **Create draft manually** (Accept as draft is the demo path) · **Reject document**

**EXPECTED RESULT:**

- Toast: **Draft created from suggestion. It is not approved.**
- Document status moves toward **Linked**
- Hint on the form: approval is a separate step

**SAY TO CLIENT:**  
“That created a draft expense only. Nothing hits the official reports until we approve it.”

**IF THIS FAILS:** If **Accept as draft** is disabled, a required field is empty — usually Date, Category, Amount, or Party. Fix the highlighted fields. Do not click Reject.

---

### STEP 14 — Approve the Keells expense

**SCREEN:** Document review (or any page)

**CLICK:** Left nav **Expenses**

**EXPECTED RESULT:** `/app/expenses`. Title **Expenses**. Header still shows Cedar Café.

**SELECT:** Status → **Draft**

**VERIFY THE ROW:**

| Date | Vendor | Amount | Status |
|---|---|---|---|
| 2026-09-05 | Keells Super | 4,850 (locale format) | DRAFT |

**CLICK:** **Approve** on that Keells Super row

There is **no confirmation dialog**.

**EXPECTED RESULT:**

- Toast: **Expense approved**
- Status column: **APPROVED**

**SAY TO CLIENT:**  
“Approved amounts are what the profit and loss and bank matching use.”

**DO NOT CLICK:** **Void** · **Documents** (unless the client asks to see the linked PDF)

**IF THIS FAILS:** Confirm Status filter is **Draft** and the vendor is **Keells Super**, not **Demo Seed Keells Aug** (that August row is already approved and will not show under Draft).

---

### STEP 15 — Create the POS income (needed for the bank credit)

**SCREEN:** Expenses

**CLICK:** Left nav **Income**

**EXPECTED RESULT:** `/app/income`. Section **Create draft**.

**ENTER / SELECT (Create draft form only):**

| Field | Value |
|---|---|
| Date | Calendar → **September 8, 2026** |
| Category | **Café sales** |
| Amount | **18500** |
| Customer | **Card settlement** |
| Payment | **Card** (not Bank transfer — the default is Bank transfer) |
| Description | LEAVE BLANK |

**CLICK:** **Create draft**

**EXPECTED RESULT:** Toast **Income draft created**.

**SELECT:** Status → **Draft**

**CLICK:** **Approve** on the **Card settlement** / **18,500** row

**EXPECTED RESULT:** Toast **Income approved**. Status **APPROVED**.

**SAY TO CLIENT:**  
“That 18,500 is the card settlement we will later see as a credit on the bank statement.”

**DO NOT:** Approve the August **Demo Seed Card Aug** row (it is already approved).

---

### STEP 16 — Create the CEB expense (needed for the bank debit)

**SCREEN:** Income

**CLICK:** Left nav **Expenses**

**ENTER / SELECT (Create draft form):**

| Field | Value |
|---|---|
| Date | Calendar → **September 10, 2026** |
| Category | **Utilities** |
| Amount | **6200** |
| Vendor | **CEB** |
| Description | LEAVE BLANK |

**CLICK:** **Create draft**

**EXPECTED RESULT:** Toast **Expense draft created**.

**SELECT:** Status → **Draft**

**VERIFY:** Vendor **CEB**, amount **6,200**, date **2026-09-10**.

**CLICK:** **Approve** on the CEB row

**EXPECTED RESULT:** Toast **Expense approved**. Status **APPROVED**.

**SAY TO CLIENT:**  
“Keells came from a document. These two were entered to match the bank statement we are about to import — same books, different evidence paths.”

**DO NOT:** Create a bank-fee expense. The 250 fee will be ignored in reconciliation.

---

## D. Financial reporting checkpoint (~2 min)

### STEP 17 — Run Profit & Loss

**SCREEN:** Any firm page

**CLICK:** Left nav **Reports** (group **Reporting**)

**EXPECTED RESULT:** `/app/reports`. Title **Profit & Loss**. Report tabs: **Profit & Loss** · **Income** · **Expenses** · **Trends**.

**SELECT:**

| Field | Value |
|---|---|
| Client | **Cedar Café (Pvt) Ltd** |
| From | **1 September 2026** (month start is the default) |
| To | **today** is acceptable (17 Sep 2026) **or** **30 September 2026** |
| Compare from / Compare to | LEAVE DEFAULT / blank |

**CLICK:** **Run**

**DO NOT CLICK:** CSV / Excel unless the client asks.

---

### STEP 18 — Verify September totals — GATE FOR BANKING

**SCREEN:** Profit & Loss

**VERIFY:**

| Line | Number |
|---|---|
| Client heading | Cedar Café (Pvt) Ltd |
| Currency | LKR |
| Income · INC-SALES Café sales | **18,500.00** |
| **Total Income** | **18,500.00** |
| Expenses · Food & beverage supplies | **4,850.00** |
| Expenses · Utilities | **6,200.00** |
| **Total Expenses** | **11,050.00** |
| **NET PROFIT** | **7,450.00** |

**SAY TO CLIENT:**  
“These totals are approved activity only. Drafts never appear here.”

**IF THESE VALUES DO NOT APPEAR, DO NOT CONTINUE TO BANKING.**

**First thing to check:**

1. **Run** was clicked with Client = Cedar Café and dates covering 5–10 September 2026.  
2. Expenses Status **All** or **Approved**: Keells Super 4,850 **APPROVED** and CEB 6,200 **APPROVED**.  
3. Income: Card settlement 18,500 **APPROVED**.  
4. You are not looking at August (45,000 / 9,200).

Fix approvals, then **Run** again. If still wrong, **STOP** and use the reset procedure. Importing the bank CSV on a wrong ledger cannot be undone cleanly.

---

**IF CLIENT ASKS (after P&L):**

- *Can one firm manage multiple clients?* Yes. Client-scoped records. Header **Active client** / per-page Client filters.
- *Is this a full ERP?* No. It is a document-to-close bookkeeping workspace, not inventory/payroll/ERP.
- *Does it support a general ledger?* It uses income/expense categories and approved totals, not a full double-entry chart of accounts.

---

## E. Bank statement import (~3 min)

### STEP 19 — Open Banking and show the account

**SCREEN:** Reports

**CLICK:** Left nav **Banking**

**EXPECTED RESULT:** `/app/banking`. Title **Banking**. Tabs: **Accounts** · **Import** · **Reconciliation**.

**SELECT:** Client **Cedar Café (Pvt) Ltd** if not already selected.

**VERIFY (Accounts tab, default):**

| Bank | Account | Number | Currency | Status |
|---|---|---|---|---|
| Commercial Bank | Cedar Café Operating | ****4521 | LKR | Active |

**SAY TO CLIENT:**  
“We store the bank identity, not internet-banking passwords.”

**DO NOT:** Fill **Add bank account** or click **Save account**.

---

### STEP 20 — Choose the CSV

**SCREEN:** Banking

**CLICK:** Tab **Import**

**SELECT:** Bank account → **Commercial Bank · Cedar Café Operating**

**CLICK:** **Choose file**  
(or drop onto **Drop bank CSV here or browse**)

**SELECT FILE:** `demo/files/bank-sept.csv`  
Full path: `C:\Users\HP\Downloads\finance-ai-automation-platform\demo\files\bank-sept.csv`

**LEAVE DEFAULT (column mapping — matches this CSV):**

| Field | Value |
|---|---|
| Date col | 0 |
| Description col | 1 |
| Reference col | 2 |
| Debit col | 3 |
| Credit col | 4 |
| Balance col | 5 |
| Profile name (optional) | LEAVE BLANK |

**CLICK:** **Preview**

**VERIFY:**

- `Rows: 4 · Valid: 4 · Invalid: 0 · Potential duplicates: 0`
- Detected period **2026-09-05 – 2026-09-12**
- Sample lines include:
  - `2026-09-05 · KEELLS SUPER KANDY · 4850`
  - `2026-09-08 · POS SALE CARD · 18500`
  - `2026-09-10 · CEB ELECTRICITY · 6200`
  - `2026-09-12 · BANK FEE · 250`

**SAY TO CLIENT:**  
“Preview does not post anything. We check the file before it hits the register.”

**IF THIS FAILS:** Wrong file or mapping. Do not click Import until Valid is 4.

---

### STEP 21 — Import the four lines

**SCREEN:** Banking → Import (preview visible)

**CLICK:** **Import**

The button label is **Import**, not “Import 4 Transactions”.

**EXPECTED RESULT:**

- Toast: **Import completed**
- UI switches to tab **Reconciliation** automatically

**DO NOT:** Click Import again.  
**DO NOT:** Re-select the same CSV.

**IF THIS FAILS with duplicates:** **STOP.** The database already consumed this statement. Do not keep retrying. Reset + re-seed before restarting the demo.

---

## F. Reconciliation (~4–5 min)

### STEP 22 — Confirm you are on Reconciliation

**SCREEN:** Banking

If not already there: **CLICK** tab **Reconciliation**

**LEAVE DEFAULT:**

- Bank account: **Commercial Bank · Cedar Café Operating**
- Status: **All**
- From / To: current month (September 2026)

**CLICK:** **Refresh** only if cards are empty.

**VERIFY:** Four reconcile cards exist.

Suggested-match text looks like:

`EXPENSE · Keells Super · 4850 · 2026-09-05 (HIGH)`  
(amount/confidence formatting **VERIFY BEFORE DEMO**; the **Confirm** button is what you click.)

---

### STEP 23 — Match Keells 4,850

**FIND CARD:** Date **2026-09-05** · **KEELLS SUPER KANDY** · amount **-4850** (debit)

**VERIFY:** Suggested match is the **Keells Super** expense, amount **4850**, date **2026-09-05**.

**CLICK:** **Confirm**

**EXPECTED RESULT:** Status badge **Matched**.

**DO NOT CLICK:** Reject · Create expense (draft) · Request document · Ignore · Unmatch

---

### STEP 24 — Match POS 18,500

**FIND CARD:** Date **2026-09-08** · **POS SALE CARD** · amount **+18500** (credit)

**VERIFY:** Suggested match is the **Card settlement** income, amount **18500**, date **2026-09-08**.

**CLICK:** **Confirm**

**EXPECTED RESULT:** Status badge **Matched**.

---

### STEP 25 — Match CEB 6,200

**FIND CARD:** Date **2026-09-10** · **CEB ELECTRICITY** · amount **-6200**

**VERIFY:** Suggested match is the **CEB** expense, amount **6200**, date **2026-09-10**.

**CLICK:** **Confirm**

**EXPECTED RESULT:** Status badge **Matched**.

---

### STEP 26 — Ignore the bank fee 250

**FIND CARD:** Date **2026-09-12** · **BANK FEE** · amount **-250**

**EXPECTED:** No useful expense match (we did not post 250). Status **Unmatched** (or similar).

**CLICK:** **Ignore**

**ENTER:**

- Reason for ignoring: `Bank charge — no matching ledger transaction required for this demo.`

**CLICK:** **Ignore line**

**EXPECTED RESULT:** Status badge **Ignored**.

**DO NOT CLICK:** Create expense (draft) for the fee (that would change P&L).  
**DO NOT:** Claim that Ignored means Matched.

**SAY TO CLIENT:**  
“Ignore is an explicit resolution with a reason. It is not a match.”

---

### STEP 27 — Show 100% resolved

**SCREEN:** Banking → Reconciliation (top of tab)

**VERIFY:**

- **Reconciliation progress** value: **100% resolved**
- Metric card **Reconciliation**: **100%**
- Matched **3** · Ignored **1** · Unmatched **0**

**SAY TO CLIENT:**  
“Every imported bank line has now either been matched to an existing financial record or explicitly resolved.”

**IF THIS FAILS:** A card is still Suggested/Unmatched. Confirm or Ignore that card. Do not import again.

---

### STEP 28 — Return to P&L — numbers must not change

**CLICK:** Left nav **Reports**

**SELECT:** Client **Cedar Café (Pvt) Ltd** · September date range (same as STEP 17)

**CLICK:** **Run**

**VERIFY:** Still **18,500.00** / **11,050.00** / **7,450.00**

**SAY TO CLIENT:**  
“Reconciliation links bank evidence to accounting records. It does not post a second expense or a second sale.”

**IF CLIENT ASKS:** *Does reconciliation create another expense?* No — **Confirm** links; **Ignore** does not post; **Create expense (draft)** would, and we did not use it for the fee.

---

## G. Month-end control — show the blocker (~2 min)

### STEP 29 — Open September close workspace (before resolving rent)

**CLICK:** Left nav **Close** (group **Period close**)

**EXPECTED RESULT:** `/app/close`. Title **Month-end close**.

**ENTER / SELECT:**

| Field | Value |
|---|---|
| Year | **2026** |
| Month | **September** |
| Search client | LEAVE BLANK |
| Status | **All** |

**CLICK:** **Refresh**

**VERIFY ROW:** Client **Cedar Café (Pvt) Ltd** · Period **September 2026** · Blockers **> 0** · Status not Closed

**CLICK:** **Open workspace**  
(If no period exists yet, this creates September and then opens it.)

---

### STEP 30 — Show that close is blocked on purpose

**SCREEN:** Period workspace (title **Cedar Café (Pvt) Ltd**, subtitle **September 2026 · 2026-09-01 – 2026-09-30**)

**VERIFY — Readiness checks** (✓ pass / ✗ fail):

| Check | Expect now |
|---|---|
| All period transactions are approved | PASS (✓) |
| Period documents have been reviewed | PASS (✓) |
| Financial documents are linked or dismissed | PASS (✓) |
| Missing-document requests are completed | **FAIL (✗)** |
| Bank statement imported for period | PASS (✓) (warning-class; imported) |
| Bank reconciliation is complete | PASS (✓) |
| Approved transactions have supporting documents | May show as not passed — this is a **WARNING**, not a close blocker (POS and CEB have no files) |

**BLOCKING ISSUES:**

**1 missing-document request is still open.**

**VERIFY Document requests list:**

`Please upload the September shop rent invoice for Cedar Café. · PURCHASE_INVOICE · OPEN · due 2026-09-20`

**VERIFY:** Button **Close period** is **disabled**.  
Hint: **Close is disabled until blockers are resolved.**

**SAY TO CLIENT:**  
“This is intentional. The system prevents us from closing a period while required month-end work remains unresolved.”

**DO NOT CLICK:** Close period (it should be disabled) · Cancel on the rent request · Start review · Reopen period · Request (do not create a second request)

**IF CLIENT ASKS:** *Can users change a closed month?* Not after close, except an ADMIN reopen with a reason. We are not closed yet.

---

## H. Business owner supplies evidence (~3–4 min)

### STEP 31 — Log out as Priya

**SCREEN:** Period workspace (or any page)

**CLICK:** Top-right **Logout**  
(There is **no** user-menu dropdown. The control is the **Logout** button.)

**EXPECTED RESULT:** `/login` · Sign in card.

**VERIFY:** You are signed out.

---

### STEP 32 — Sign in as the business owner

**SCREEN:** Sign in

**ENTER:**

- Email: `amaya@cedarcafe.lk`
- Password: `DemoPass123!`

**CLICK:** **Sign in**

**EXPECTED RESULT:** **Your business hub** (`/app/owner`). Breadcrumb **Client portal**.

**VERIFY:**

- Name: **Amaya Silva**
- Role: **Business Owner**
- Left nav is **not** Priya’s menu. It is: **Home** · **My documents** · **Expenses** · **Income** · **Summary**
- **No** Banking, Close, Audit log, Users, Firm settings

**SAY TO CLIENT:**  
“The owner sees requests and approved totals. They do not run the firm’s close or reconciliation.”

If you are not on Home: **CLICK** left nav **Home**.

---

### STEP 33 — Upload the September rent invoice

**SCREEN:** Your business hub

**FIND CARD:** Title **September rent invoice**

**VERIFY:**

- Status **Open**
- Type **PURCHASE_INVOICE**
- Due **2026-09-20**
- Text: **Please upload the September shop rent invoice for Cedar Café.**
- Steps: **1 · Requested** is active

**CLICK:** **Choose file** (in that card’s dropzone)

**SELECT FILE:** `demo/files/utility-bill.pdf`  
There is no dedicated rent file. This is the file the current demo README and e2e rehearsal use.

**CLICK:** **Upload file**  
(If the status were already Uploaded, the button would say **Replace file** — it should still say **Upload file**.)

**EXPECTED RESULT:**

- Status badge **Uploaded**
- Step **2 · Uploaded** active
- Hint: **File received — your accountant will review and mark complete.**

**SAY TO CLIENT:**  
“The business owner supplies the requested evidence, while the accounting firm retains review and control.”

**DO NOT:** Upload random extra receipts in **Upload other documents**.  
**DO NOT:** Say the request is Completed. It is **Uploaded** until Priya marks it complete.

**IF THIS FAILS:** Confirm the file is a PDF/image and the card is still OPEN. Do not log in as Priya to upload unless Amaya cannot; the point of this scene is the owner role.

---

### STEP 34 — Log out as Amaya

**CLICK:** **Logout**

**EXPECTED RESULT:** Sign in screen.

---

## I. Accountant completes the request and closes (~3–4 min)

### STEP 35 — Sign in as Priya again

**SCREEN:** Sign in

**ENTER:**

- Email: `priya@harborledger.demo`
- Password: `DemoPass123!`

**CLICK:** **Sign in**

**EXPECTED RESULT:** Dashboard as **Priya Fernando** / **Admin**.

---

### STEP 36 — Complete the uploaded request

**CLICK:** Left nav **Close**

**SELECT:** Year **2026** · Month **September**

**CLICK:** **Refresh**

**CLICK:** **Open workspace** on Cedar Café

**FIND:** Document requests line still describing the rent invoice, now status **UPLOADED**

**CLICK:** **Mark complete**  
(The label is **Mark complete**, not Complete.)

**EXPECTED RESULT:** Request status becomes **COMPLETED**. The open-request blocker disappears after reload.

**DO NOT CLICK:** **Cancel**

**SAY TO CLIENT:**  
“Upload is not the same as complete. The firm accepts the evidence, then the period can close.”

---

### STEP 37 — Confirm readiness, then close September

**SCREEN:** Same period workspace (reloaded)

**VERIFY checklist:**

| Check | Expect |
|---|---|
| All period transactions are approved | PASS |
| Period documents have been reviewed | PASS |
| Financial documents are linked or dismissed | PASS |
| Missing-document requests are completed | **PASS** |
| Bank reconciliation is complete | PASS |

**VERIFY:** Hint **No blockers. You may close after final review.**  
**Close period** is **enabled**.

Warnings may still list approved transactions without documents. **That does not block close.**

**ENTER:**

- Close note (optional): `September 2026 demo close after reconciliation`

**CLICK:** **Close period**

There is no separate OK dialog. Submit is the button itself.

**EXPECTED RESULT:**

- Status badge **Closed**
- Text: **Closed period · Finalized bookkeeping period (not an audited statement)**
- Closed by **Priya Fernando**

**SAY TO CLIENT:**  
“September is now a finalized bookkeeping period — not a legal audit certificate.”

**DO NOT CLICK:** **Reopen period**

**IF THIS FAILS:** Read **Blocking issues**. If the rent request is still OPEN/UPLOADED, click **Mark complete**. Then try **Close period** again. Do not reopen anything.

---

### STEP 38 — Closed-period protection (safe live demo)

**SCREEN:** Closed September workspace

**CLICK:** Left nav **Expenses**

**ENTER (Create draft):**

| Field | Value |
|---|---|
| Date | **15 September 2026** |
| Category | **Utilities** |
| Amount | **100** |
| Vendor | **Should fail** |
| Description | LEAVE BLANK |

**CLICK:** **Create draft**

**EXPECTED RESULT:** Error toast / message:

**This date belongs to a closed bookkeeping period. Reopen the period to change financial data.**

**VERIFY:** No new September 15 expense in the grid.

**SAY TO CLIENT:**  
“The books for a closed month cannot be quietly changed.”

**Cleanup:** None. The save is rejected.

**DO NOT:** Reopen the period to make the save work.

---

**IF CLIENT ASKS (close):**

- *What happens if an accountant makes a mistake?* Drafts can be corrected before approve; approved items can be **Void** (do not demo Void now); a closed month needs **ADMIN reopen** with a reason, which is audited.
- *Can users change a closed month?* Financial writes for dates in that month are blocked until an administrator reopens.

---

## J. Reports worth showing + audit (~3 min)

### STEP 39 — P&L after close (unchanged totals)

**CLICK:** Left nav **Reports**

**CLICK:** Tab **Profit & Loss** if not already there

**SELECT:** Cedar Café · September range

**CLICK:** **Run**

**VERIFY:** Still **18,500.00** / **11,050.00** / **7,450.00**

**SAY TO CLIENT:**  
“Closing does not recalculate profit. It locks the month so the report stays stable.”

---

### STEP 40 — Trends (worth 30–45 seconds)

**CLICK:** Report tab **Trends**

**SELECT:** Client **Cedar Café (Pvt) Ltd**  
Widen **From** to **1 August 2026** if the default is only September, then **Run**.

**VERIFY:** August bar (seeded 45,000 income / 9,200 expense) vs September (18,500 / 11,050).

**SAY TO CLIENT:**  
“Prior months stay available for comparison after we close September.”

**SKIP unless asked:** report tabs **Income** and **Expenses** (they drill the same approved figures).

---

### STEP 41 — Audit trail

**CLICK:** Left nav **Audit log** (group **Administration**)

**EXPECTED RESULT:** `/app/audit`. Title **Audit log**. Newest first.

**POINT AT recent rows (wording is the action with underscores turned into spaces):**

| What (typical) | Why you point at it |
|---|---|
| EXPENSE APPROVED | Keells / CEB approval |
| INCOME APPROVED | POS approval |
| BANK IMPORT COMPLETED | CSV import |
| RECONCILIATION CONFIRMED | The three matches |
| BANK TRANSACTION IGNORED | Bank fee |
| DOCUMENT REQUEST UPLOADED | Amaya |
| DOCUMENT REQUEST COMPLETED | Priya Mark complete |
| PERIOD CLOSED | September close |

Who column shows **role** (e.g. ADMIN / BUSINESS OWNER), not the person’s name.

**SAY TO CLIENT:**  
“If something changed, we can see what happened and whether it succeeded.”

**IF CLIENT ASKS:** *Is there an audit trail?* Yes — this page. It is not a substitute for a statutory audit.

---

### STEP 42 — End the main demo

**SAY TO CLIENT:**  
“That is the connected cycle: evidence, books, bank, client request, month-end control, close, report, and audit.”

**STOP** unless they ask about users/clients (optional section below).

**DO NOT:** Log in as Nimal unless asked.  
**DO NOT:** Seed again.  
**DO NOT:** Reopen September.

---

## OPTIONAL — IF CLIENT ASKS ABOUT USERS / CLIENT MANAGEMENT

Read-only. Do not mutate.

### Users

**CLICK:** Left nav **Users**

**POINT AT rows:**

- Priya Fernando · `priya@harborledger.demo` · ADMIN · Active  
- Nimal Perera · `nimal@harborledger.demo` · ACCOUNTANT · Active  
- Amaya Silva · `amaya@cedarcafe.lk` · BUSINESS_OWNER · Active  

**SAY TO CLIENT:**  
“Roles limit the menu. Access is also granted per client.”

**DO NOT CLICK:** **Create user** · **Deactivate**

### Clients

**CLICK:** Left nav **Clients**

**POINT AT:** **Cedar Café (Pvt) Ltd** · accounts@cedarcafe.lk · **Active**

**SAY TO CLIENT:**  
“The firm can add more SMEs. Each client keeps its own documents, books, bank, and close.”

**DO NOT CLICK:** **Create** · **Deactivate**

### Categories (only if asked)

**CLICK:** **Categories**

**POINT AT** the five seeded names. Do not create new ones.

---

## IF CLIENT ASKS — SHORT ANSWERS

Aligned with **current** product behaviour. Do not oversell.

**Can one accounting firm manage multiple clients?**  
Yes. Firm tenancy plus client-scoped records. Switch with **Active client** / Client filters.

**Can a business owner see all accounting information?**  
No. Amaya sees Home/requests, her documents, expenses/income, and a simple summary. No Banking, Close, Users, or Audit log.

**Does reconciliation create another expense?**  
Not when you **Confirm** a suggestion or **Ignore** a line. Only **Create expense (draft)** would add a ledger row. We did not do that for the bank fee.

**What happens to unmatched bank transactions?**  
They stay unmatched/suggested/pending and **block month-end close** until matched, ignored, or otherwise resolved.

**Can users change a closed month?**  
Financial writes for dates in a **CLOSED** period are rejected (`PERIOD_CLOSED`). Only an **ADMIN** can **Reopen period** with a required reason. That is audited.

**Can documents be requested from clients?**  
Yes. Accountant requests → owner uploads → status **Uploaded** → accountant **Mark complete**. Open/uploaded requests block close.

**Is this a full ERP?**  
No. Document-to-close bookkeeping for accounting firms. Not inventory, payroll, or full ERP.

**Does it support a general ledger?**  
Not a full double-entry GL. Income and expense categories feed P&L. Do not claim a complete chart of accounts.

**What happens if an accountant makes a mistake?**  
Correct drafts before approve. Approved items can be voided (not shown in this demo). Closed months need a controlled reopen. Audit log records the actions.

**Is there an audit trail?**  
Yes. **Audit log** lists what changed, the actor’s role, and the result.

---

# 15-MINUTE DEMO VERSION

Use this exact subset if time is short. Same credentials, files, and numbers.

**Target: 15 minutes.** Skip speeches. Skip Trends, Audit, and the closed-period write test unless asked.

| Time | Do these steps |
|---|---|
| 0:00 | Opening statement. STEP 6–7 login Priya. |
| 0:45 | STEP 9 point at Cedar Café (skip Dashboard tour). |
| 1:00 | STEP 10–14 Keells Review → Accept as draft → Approve. |
| 4:00 | STEP 15–16 POS 18500 Card Approve + CEB 6200 Approve. |
| 6:00 | STEP 17–18 P&L **Run**. Gate: 18,500 / 11,050 / 7,450. |
| 7:00 | STEP 19–21 Import `bank-sept.csv` (skip lingering on Accounts). |
| 8:30 | STEP 23–27 Confirm ×3, Ignore fee, 100%. |
| 11:00 | STEP 29–30 Close workspace, show blocker, **do not close**. |
| 12:00 | STEP 31–33 Logout, Amaya, Upload file `utility-bill.pdf`. |
| 13:30 | STEP 34–37 Priya, Mark complete, Close period. |
| 15:00 | Stop. Offer Audit/Trends if they want another minute. |

Exact clicks for the cut-down path are the same labels as the full steps above. Do not invent a shorter button path.

---

## RESET (only if the demo database is already consumed)

If someone imported the CSV, approved Keells, or closed September on this database:

```powershell
docker compose down -v
docker compose up -d postgres
```

Restart backend and frontend (STEPS 2–3), then:

```powershell
python demo/seed_demo.py
```

Then STOP at the Sign in screen (STEP 5).

---

## DANGER BOARD

| ⚠ DO NOT | Why |
|---|---|
| Seed mid-demo | Can confuse state; will not undo bank import |
| Import `bank-sept.csv` twice | Duplicate import — demo is consumed |
| Upload a second Keells file | Duplicate evidence |
| Approve / Void the August seed rows | Wrong month, wrong story |
| Click **Void** on September items | Breaks P&L and matching |
| Click **Reject document** / **Reject suggestion** | You lose the happy path |
| Click **Unmatch** after Confirm | Breaks 100% |
| Create expense from BANK FEE | Changes P&L to 11,300 |
| Close September before the blocker scene | You cannot show month-end control |
| Cancel the rent request | You skip the owner story |
| Reopen period | Unlocks writes; muddy story |
| Platform admin | Out of scope |
| Refresh in the middle of Import/Approve | Can double-submit in some browsers |
| Port **4201** | Not this app’s default |
| Any login page that is not **Finance Platform** | Wrong product |

---

## SOURCES AND CONFLICTS

| Topic | Historical / other docs | Current verified behaviour |
|---|---|---|
| Frontend port | 4201 in some older notes | **4200** (`npm start`, compose, CORS, e2e) |
| Seed summary URL | `demo/seed_demo.py` prints 4200 | **4200** — correct |
| Login button | “Sign In” | **Sign in** |
| Save expense | “Save Expense” | Document path: **Accept as draft**; ledger path: **Create draft** then **Approve** |
| Bank import button | “Import 4 Transactions” | **Import** |
| File picker | “Choose File” | **Choose file** (dropzone) |
| P&L button | “Run Report” | **Run** |
| Logout | “user menu → Log out” | Top-bar button **Logout** |
| Owner nav | e2e looks for “Requested documents” | BUSINESS_OWNER nav is **Home**. She lands on `/app/owner` automatically |
| Owner upload button | e2e “Upload” | **Upload file** |
| Close blocker text | e2e `/open document request/` | **1 missing-document request is still open.** |
| Complete request | “Complete” | **Mark complete** |
| Rent file | imaginary rent PDF | **`demo/files/utility-bill.pdf`** |
| Named FINAL_* runbooks | requested | **Not in this repository** |

`FINAL_CLIENT_DEMO_RUNBOOK.md` was **not updated** because it does not exist. This file is the operator manual.

---

## VERIFY BEFORE DEMO (short list)

1. Postgres password matches backend.  
2. http://localhost:8080/api/v1/health/ready is OK.  
3. http://localhost:4200/login shows **Finance Platform**.  
4. Date calendar locale: pick **5 / 8 / 10 September 2026** from the picker.  
5. P&L number formatting (`18,500.00` vs `18500`).  
6. After import, suggestion labels include Keells Super / Card settlement / CEB — if not, **do not improvise**; reset.  
7. Do not run this mutating path on the machine you just reset for the client.
