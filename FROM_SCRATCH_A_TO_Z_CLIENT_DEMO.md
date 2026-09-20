# From-scratch A-to-Z client demo — Finance Platform

**Purpose:** Show a **new subscriber journey** from **no account** → registered firm → first client → bookkeeping → bank reconciliation → document request → business owner upload → month-end close → reports → audit.

**Story names (use consistently):**

| Role | Name | Email |
| --- | --- | --- |
| Accounting firm | Summit Accounting Partners | — |
| Firm admin | Daniel Perera | daniel.perera@summit-demo.example.com |
| Client business | Green Leaf Café (Pvt) Ltd | accounts@greenleaf-demo.example.com |
| Business owner | Maya Fernando | maya@greenleaf-demo.example.com |
| Password (all) | `DemoPass123!` | |

---

## Repository verification

| Check | Result |
| --- | --- |
| Product | **Finance Platform** (Document-to-Close) |
| Frontend | **Angular** (`frontend/`) |
| Backend | **Spring Boot modular monolith** (`backend/platform-app`) |
| Database | **PostgreSQL** + Flyway |
| Hierarchy | **Firm → Clients → Users → financial records** |
| Wrong repo? | Not CareHome / Temple / etc. — **correct repo** |

**Do not** use Harbor Ledger Partners, Priya, Cedar Café, or Amaya as the **main** demo path. Reuse **files** under `demo/files/` only.

**Before every presentation:** empty disposable DB — see `FROM_SCRATCH_DEMO_RESET_GUIDE.md`.

---

## Discovery — what registration actually does

Inspected: `register.page.ts`, `RegistrationService`, `FirmService`, `UserRegistrationService`, `application.yml`.

| Question | Answer |
| --- | --- |
| Can a new user self-register a firm? | **Yes** — public `POST /api/v1/auth/register` + **Register a firm** on login page |
| Registration form fields | **Firm name**, **Your name**, **Email**, **Password**, **Confirm password** only |
| Registration number / terms / currency on form? | **No** (API accepts optional `registrationNo`; UI does not send it) |
| Creates firm? | **Yes** |
| Creates ADMIN user? | **Yes** |
| Creates subscription? | **Yes** — `SubscriptionService.createDefaultForFirm` → **STARTER** trial (14 days default) |
| Default categories? | **No** — must create in **Categories** |
| Default currency / timezone / FY? | **On firm record:** LKR, Asia/Colombo, FY start month **4 (April)** — editable under **Firm settings** |
| Email verification required? | **Default `false`** (`APP_AUTH_EMAIL_VERIFICATION_REQUIRED`) — admin can login immediately after register |
| Auto-login after register? | **No** — UI navigates to **`/login`**; README confirms no JWT on register |
| Empty dashboard? | **Yes** — zeros until clients/transactions exist |
| Zero clients initially? | **Yes** |
| Admin can create client immediately? | **Yes** — **Clients** → **Create** (name required) |
| Mandatory before first transaction? | **Active client** + **categories** for expense/income forms |

**Email verification (if enabled in env):** `EmailVerificationService` sends link via `APP_EMAIL_PROVIDER` (`log` → backend console). Flow: register → copy link from logs → open `/verify-email?token=…` → sign in.

---

## Verification performed

| Method | Result |
| --- | --- |
| Code inspection | Full UI/API path mapped (this document) |
| Integration tests (Testcontainers) | `GoldenPathWorkflowIntegrationTest` **passed** — register firm admin → client → category → document → approve → bank → reconcile → close → P&L |
| Live browser on localhost | **Not run** (backend was down in agent session) |
| Backend code changes for demo | **None** |

### Known product gap (blocks pure UI owner story)

**Users page does not assign clients.** `CreateUserRequest` supports `clientIds` / `clientAccess`, but `users.page.ts` only sends name, email, password, role. Without assignment, a business owner **cannot see** the client or requests (`ClientAccessService.assertHasClientMembership`).

`demo/seed_demo.py` documents this: *“Users page cannot do this in UI”* and uses `PUT /api/v1/users/{id}/client-access`.

**Demo requirement:** one **admin-only API step** (Swagger or Network tab) before owner login — **Step 8B** below. Classified as **missing UI capability (C)**, not seed-only data.

---

## Verdict

### **FROM-SCRATCH DEMO CONDITIONALLY READY**

Complete **accountant/admin** path is supported in UI and proven by integration tests. **Business owner** path requires **Step 8B (API client access)** until the Users UI is extended.

---

## Final checklist (YES/NO)

| Item | Result |
| --- | --- |
| Registration works | **YES** |
| New firm created | **YES** |
| New admin login works | **YES** (with default email verification off) |
| Email verification requirement | **Off by default**; enable only with log/SMTP procedure |
| Empty dashboard works | **YES** |
| Client creation works | **YES** |
| New client immediately selectable | **YES** (header **Active client** after first client) |
| Default categories available | **NO** — create manually |
| Category creation works | **YES** |
| Business Owner creation works | **YES** (UI) |
| Business Owner can login | **YES** (password set at creation) |
| Client assignment works | **NO in UI** — **YES via API** (Step 8B) |
| Document upload for new client | **YES** |
| Document review works | **YES** (manual draft when AI off) |
| Expense draft / approval | **YES** |
| Income creation / approval | **YES** |
| P&L for new client | **YES** (approved only) |
| Bank account creation | **YES** |
| CSV import | **YES** (`bank-sept.csv` compatible with default mapping) |
| Reconciliation | **YES** |
| 100% resolution achievable | **YES** (3 match + 1 ignore) |
| Document request creation | **YES** (close workspace) |
| Owner sees new request | **YES** after Step 8B |
| Owner upload works | **YES** |
| Admin completion works | **YES** — **Mark complete** |
| Month-end blocker (open request) | **YES** — OPEN and UPLOADED requests block close |
| Period close works | **YES** when blockers cleared |
| Closed-period protection | **YES** |
| Final P&L amounts | **18,500 / 11,050 / 7,450** (if demo data followed) |
| Audit trail works | **YES** (firm/client scoped) |
| Seed-only dependency | **None** for core ledger; **client access** is API-only gap |
| UI impossible steps | **Assign client to business owner** |
| Exact blockers | Users UI lacks client access; firm name globally unique |
| Demo firm / client / admin / owner | Summit / Green Leaf / Daniel / Maya (emails above) |
| Duration | Full **35–45 min**; Short **~20 min** |
| This file created | **YES** |
| DATA_CARD / CHEAT_SHEET / RESET created | **YES** |
| Backend / API / logic changed | **NO / NO / NO** |
| Production deployed | **NO** |
| Recommended next action | Add **client assignment** to Users UI; rehearse once on empty DB including Step 8B |

---

## Weaknesses a client might probe

| Topic | Risk |
| --- | --- |
| Duplicate firm name | Second registration with same name → **409 conflict** |
| Duplicate admin email | Global email uniqueness |
| No categories | Expense/income forms empty until categories created |
| Owner without API step | Empty owner portal — looks broken |
| STARTER plan | **3 users max** — admin + owner OK; avoid extra staff in demo |
| AI enabled on firm but provider off | Document may show extraction disabled — use **manual draft** |
| Open request after owner upload | Close still blocked until admin **Mark complete** (UPLOADED counts as open) |
| Bank account exists | Reconciliation becomes **close blocker** until resolved |
| Trends report | No prior months — empty or dull; prefer **P&L** |

---

## Duration

| Version | Time | Notes |
| --- | --- | --- |
| **Full** | 35–45 min | Registration narrative, firm settings, readiness checklist, audit |
| **Short** | ~20 min | Keep register, client, categories, owner+API, full ledger, close |

---

# LIVE DEMO STEPS

**Base URL:** `http://localhost:4200`  
**API (Swagger local):** `http://localhost:8080/swagger-ui/index.html`

---

## STEP 1 — Open login

**OPEN:** `http://localhost:4200/login`

**SAY TO CLIENT:** *“Imagine you subscribed today — we start with no account.”*

**VERIFY:** Title **Sign in**, link **Register a firm**.

---

## STEP 2 — Register firm

**CLICK:** **Register a firm**

**EXPECTED:** Page **Register firm** — subtitle *Create the firm administrator account, then sign in.*

**ENTER:**

| Field | Value |
| --- | --- |
| Firm name | Summit Accounting Partners |
| Your name | Daniel Perera |
| Email | daniel.perera@summit-demo.example.com |
| Password | DemoPass123! |
| Confirm password | DemoPass123! |

**CLICK:** **Create firm**

**EXPECTED:** Navigate to **Sign in** (no automatic session).

**IF THIS FAILS:** “Firm name already exists” → reset DB or use a unique firm name suffix.

**DO NOT:** Expect currency/timezone on this form.

---

## STEP 3 — Sign in as new admin

**ENTER:**

| Field | Value |
| --- | --- |
| Email | daniel.perera@summit-demo.example.com |
| Password | DemoPass123! |

**CLICK:** **Sign in**

**EXPECTED:** **Dashboard** at `/app/dashboard`.

**VERIFY:**

- Role behaves as firm admin (nav: Clients, Categories, Users, Banking, Month-end close, Audit).
- **Practice overview:** Active clients **0** (or minimal until client created).
- Work queue counts **0**.

**SAY TO CLIENT:** *“This is day one — an empty workspace until we onboard clients.”*

---

## STEP 4 — Optional firm settings

**NAV:** **Firm settings** (sidebar)

**VERIFY:** Currency **LKR**, Timezone **Asia/Colombo** (defaults).

**OPTIONAL ENTER:** Confirm **Financial year start** = April → **Save**

**SKIP IN SHORT DEMO:** OK to skip if defaults already correct.

---

## STEP 5 — Create first client

**NAV:** **Clients**

**CLICK:** **Create** (after filling row form)

**ENTER:**

| Field | Value |
| --- | --- |
| Name | Green Leaf Café (Pvt) Ltd |
| Email | accounts@greenleaf-demo.example.com |

**EXPECTED:** Toast **Client created**, row **Active**, empty state gone.

**VERIFY:**

- Header **Active client** shows Green Leaf (or select in dropdown if multiple).
- **Documents / Expenses / Income / Banking** use this client when selected.

**SAY TO CLIENT:** *“The firm now has its first business client.”*

---

## STEP 6 — Create categories

**NAV:** **Categories**

**DO NOT** assume defaults — list starts empty for a new firm.

For each row, **Create category** with values from `FROM_SCRATCH_DEMO_DATA_CARD.md` (minimum: GL-FOOD, GL-UTIL, GL-SALES; add GL-BANK, GL-RENT for rent narrative).

**VERIFY:** Categories appear in **Expenses** / **Income** category dropdowns.

---

## STEP 7 — Create business owner (UI)

**NAV:** **Users**

**ENTER:**

| Field | Value |
| --- | --- |
| Name | Maya Fernando |
| Email | maya@greenleaf-demo.example.com |
| Password | DemoPass123! |
| Confirm password | DemoPass123! |
| Role | Business owner |

**CLICK:** **Create user**

**EXPECTED:** Toast **User created**, Maya listed **Active**.

**VERIFY:** Maya can authenticate (Step 15) **only after Step 8B**.

**OPTIONAL — SKIP IN STANDARD DEMO:** Second **Accountant** user (uses STARTER user quota).

---

## STEP 8B — Assign client to owner (NOT IN UI — required)

**STATUS:** **NOT AVAILABLE IN CURRENT UI** — supported API: `PUT /api/v1/users/{userId}/client-access`

**WHY:** Business owners need `UserClientAccess` or all client APIs return access denied.

**Procedure (Swagger recommended):**

1. **POST** `/api/v1/auth/login` with Daniel’s email/password → copy `accessToken`.
2. **Authorize** Swagger with Bearer token.
3. **GET** `/api/v1/clients` → copy Green Leaf `id` → `{clientId}`.
4. **GET** `/api/v1/users` → copy Maya’s `id` → `{userId}`.
5. **PUT** `/api/v1/users/{userId}/client-access`:

```json
{
  "assignments": [
    {
      "clientId": "{clientId}",
      "accessType": "UPLOAD_ONLY"
    }
  ]
}
```

**EXPECTED:** `200` with assignment list.

**VERIFY:** Log in as Maya → owner home shows Green Leaf requests (Step 15).

**SAY TO CLIENT:** *“In production you’d assign access in user setup; today we apply the assignment through the admin API because the screen is not wired yet.”*

**DO NOT:** Skip this and use seeded Amaya.

---

## STEP 9 — Upload Keells receipt

**NAV:** **Document inbox** (`/app/documents`)

**ENTER:**

| Field | Value |
| --- | --- |
| Client | Green Leaf Café (Pvt) Ltd |
| Document type | Receipt |
| Note | Keells supplies September |
| File | `demo/files/keells-receipt.pdf` |

**CLICK:** **Upload**

**EXPECTED:** Row appears for Green Leaf.

**VERIFY:** Status may be **PROCESSING** then **NEEDS_REVIEW** or extraction disabled — with `APP_AI_ENABLED=false`, use manual path.

**SAY TO CLIENT:** *“We’re moving from setup into daily bookkeeping.”*

---

## STEP 10 — Document review → expense draft

**CLICK:** Open the document (review route)

**SCREEN:** **Document review** — source PDF, **Extracted information** (may be empty if AI off).

**ENTER** manual accounting form (example):

| Field | Value |
| --- | --- |
| Type | Expense |
| Date | 5 September 2026 |
| Category | Food & beverage supplies |
| Amount | 4850 |
| Vendor | Keells Super |
| Currency | LKR |

**CLICK:** **Create draft manually**

**EXPECTED:** Toast *Manual draft created. Approval is a separate step.*

**NAV:** **Expenses** → filter **Draft** → Keells row

**CLICK:** **Approve**

**EXPECTED:** Status **APPROVED**.

**SAY TO CLIENT:** *“Uploading evidence doesn’t approve the ledger — approval is deliberate.”*

---

## STEP 11 — Income (card settlement)

**NAV:** **Income**

**ENTER:**

| Field | Value |
| --- | --- |
| Date | 8 September 2026 |
| Category | Café sales |
| Amount | 18500 |
| Customer | Card settlement |
| Payment | Card |

**CLICK:** Create/save draft (primary submit on form)

**CLICK:** **Approve** on row

**VERIFY:** **APPROVED**.

---

## STEP 12 — Expense (CEB)

**NAV:** **Expenses**

**ENTER:**

| Field | Value |
| --- | --- |
| Date | 10 September 2026 |
| Category | Utilities |
| Amount | 6200 |
| Vendor | CEB |

**CLICK:** Create draft → **Approve**

---

## STEP 13 — Profit & Loss

**NAV:** **Profit & Loss** (`/app/reports`)

**SELECT:**

| Field | Value |
| --- | --- |
| Client | Green Leaf Café (Pvt) Ltd |
| From | 1 Sep 2026 |
| To | 30 Sep 2026 |

**CLICK:** **Run** (report filter submit)

**EXPECTED:**

| Line | Amount (LKR) |
| --- | --- |
| Total income | 18,500 |
| Total expenses | 11,050 |
| Net profit | 7,450 |

**IF THIS FAILS:** **STOP** — fix approvals, client, or dates before banking.

---

## STEP 14 — Bank account

**NAV:** **Banking** → tab **Accounts**

**ENTER:**

| Field | Value |
| --- | --- |
| Bank | Commercial Bank |
| Account name | Green Leaf Café Operating |
| Account no. (masked) | ****4521 |
| Currency | LKR |

**CLICK:** **Save account**

**VERIFY:** Account listed **Active** for Green Leaf.

---

## STEP 15 — Import CSV

**TAB:** **Import**

**SELECT:** Bank account **Green Leaf Café Operating**

**FILE:** `demo/files/bank-sept.csv`

**VERIFY mapping (defaults):** Date **0**, Description **1**, Reference **2**, Debit **3**, Credit **4**, Balance **5**

**CLICK:** **Preview** → **Import**

**EXPECTED:** Valid rows imported.

---

## STEP 16 — Reconcile

**TAB:** **Reconcile** (or reconciliation section)

**SET** date filter: 1 Sep 2026 – 30 Sep 2026

**FOR EACH** suggested match:

| Bank line | Action |
| --- | --- |
| KEELLS SUPER KANDY 4,850 | **Confirm** → Keells expense |
| POS SALE CARD 18,500 | **Confirm** → Card settlement income |
| CEB ELECTRICITY 6,200 | **Confirm** → CEB expense |
| BANK FEE 250 | **Ignore** → reason e.g. `Bank service charge — no ledger entry` |

**VERIFY summary:** **3 matched**, **1 ignored**, **0 unmatched**, **100%** resolved.

**SAY TO CLIENT:** *“Reconciliation validates the bank — it doesn’t duplicate approved entries.”*

**RUN P&L again:** same **18,500 / 11,050 / 7,450**.

---

## STEP 17 — Document request + close blocked

**NAV:** **Month-end close**

**FILTER:** Year **2026**, Month **September**

**CLICK:** **Open workspace** on Green Leaf (creates period if needed)

**SCROLL:** **Request missing document**

**ENTER:**

| Field | Value |
| --- | --- |
| What you need | Please upload the September shop rent invoice. |
| Type | Purchase invoice |
| Due | 20 September 2026 |

**CLICK:** **Request**

**VERIFY:** Request **OPEN** in list.

**RUN readiness / try Close period**

**EXPECTED:** **Close period** disabled or server rejects — blocker **missing-document requests**.

**SAY TO CLIENT:** *“The system won’t finalize the month while required evidence is outstanding.”*

---

## STEP 18 — Owner upload

**CLICK:** Sign out → **Sign in** as `maya@greenleaf-demo.example.com` / `DemoPass123!`

**EXPECTED:** Owner hub — **Documents your accountant needs** (upload-only nav).

**VERIFY:** No **Banking**, **Month-end close**, **Users**, **Audit**.

**UPLOAD:** On open request → `demo/files/utility-bill.pdf` → **Upload file**

**EXPECTED:** Status **UPLOADED** (not COMPLETED).

**DO NOT:** Claim completed until admin marks complete.

**SAY TO CLIENT:** *“The client supplies evidence without accountant admin controls.”*

---

## STEP 19 — Admin completes request

**Sign out** → **Sign in** as Daniel

**NAV:** Month-end close → same September workspace

**CLICK:** **Mark complete** on uploaded request

**EXPECTED:** **COMPLETED**

**VERIFY readiness:** document-request blocker cleared.

---

## STEP 20 — Close period

**RESOLVE** remaining blockers (drafts, unmatched bank, documents needing review — should be clear if you followed steps).

**ENTER:** Close note optional e.g. `September 2026 finalized in demo`

**CLICK:** **Close period**

**EXPECTED:** Status **CLOSED**, closed-by shows Daniel.

**SAY TO CLIENT:** *“Once controls pass, the period can be finalized.”*

---

## STEP 21 — Closed-period protection

**NAV:** **Expenses** → create **15 Sep 2026**, Utilities, **100**, Test Vendor

**EXPECTED:** Error / **PERIOD_CLOSED** — transaction not approved/persisted.

**DO NOT:** Reopen the period in the demo.

---

## STEP 22 — Final P&L

**NAV:** **Profit & Loss** — September 2026, Green Leaf

**EXPECTED:** Still **18,500 / 11,050 / 7,450**.

**Trends:** Optional — likely empty history; skip in short demo.

---

## STEP 23 — Audit trail

**NAV:** **Audit** (admin)

**FILTER:** Client Green Leaf; date range September 2026 if available

**EXPECTED events (non-exhaustive — only claim what you see):**

- Firm registered / user created  
- Client created  
- User created (Maya)  
- Document uploaded  
- Expense/income create & approve  
- Bank account / import / reconciliation actions  
- Document request create / upload / complete  
- Period close  
- Rejected post-close write (if audited)

**SAY TO CLIENT:** *“Everything we did leaves a trace.”*

---

## IF THIS FAILS — quick map

| Step | Symptom | Fix |
| --- | --- | --- |
| 2 | Conflict on firm name | Reset DB |
| 3 | Invalid credentials | Verification email? Check env |
| 8B | Owner empty | Run client-access API |
| 10 | No categories | Step 6 |
| 13 | Wrong P&L | Approve all drafts; correct dates |
| 16 | Close blocked on bank | Finish reconcile |
| 17–20 | Close blocked on requests | Complete admin **Mark complete** after upload |
| 21 | Write succeeds | Period not closed — recheck close |

---

## Related files

- `FROM_SCRATCH_DEMO_DATA_CARD.md` — all field values
- `FROM_SCRATCH_DEMO_CHEAT_SHEET.md` — one-page flow
- `FROM_SCRATCH_DEMO_RESET_GUIDE.md` — empty environment before show
