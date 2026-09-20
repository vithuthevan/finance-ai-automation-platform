# CLIENT DEMO CHEAT SHEET

One-page sequence. Full wording, fields, and recovery live in `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md`.

**URL:** http://localhost:4200  
**Password (all demo users):** `DemoPass123!`

---

## BEFORE CLIENT

1. Postgres + backend + frontend running  
2. `python demo/seed_demo.py` already done  
3. Login page shows **Finance Platform**  
4. **STOP. Do not rehearse after the final reset.**

---

## MAIN SEQUENCE (~28–32 min)

**LOGIN PRIYA**  
`priya@harborledger.demo` / `DemoPass123!` → **Sign in**

→ Dashboard (point, do not click queues)

→ Confirm **Cedar Café (Pvt) Ltd** (header + Dashboard **Client**)

→ Left nav **Documents**

→ Status **Needs review** → row **keells-receipt.pdf** → **Review**

→ ⚠ Do **not** upload another copy

→ Fill: Type **Expense** · Date **5 Sep 2026** · Category **EXP-FOOD Food & beverage supplies** · Amount **4850** · Tax **0** · Currency **LKR** · Party **Keells Super** · Reference **blank** · Description **blank**

→ **Accept as draft** → toast “Draft created from suggestion…”

→ Left nav **Expenses** → Status **Draft** → Keells Super **4,850** → **Approve** → **APPROVED**

→ Left nav **Income** → Create draft: Date **8 Sep 2026** · Category **Café sales** · Amount **18500** · Customer **Card settlement** · Payment **Card** · Description **blank** → **Create draft** → Status **Draft** → **Approve**

→ Left nav **Expenses** → Create draft: Date **10 Sep 2026** · Category **Utilities** · Amount **6200** · Vendor **CEB** → **Create draft** → Status **Draft** → **Approve**

→ Left nav **Reports** → Client **Cedar Café (Pvt) Ltd** → From **1 Sep 2026** → To **today (or 30 Sep)** → **Run**

→ **VERIFY P&L:** Income **18,500.00** / Expenses **11,050.00** / NET PROFIT **7,450.00**  
→ **If wrong: STOP. Do not open Banking.**

→ Left nav **Banking** → tab **Accounts** (point: Commercial Bank · Cedar Café Operating · ****4521)

→ tab **Import** → Bank account **Commercial Bank · Cedar Café Operating**  
→ **Choose file** → `demo/files/bank-sept.csv`  
→ Column mapping **leave defaults 0–5** → **Preview** → Valid **4** → **Import**

→ Lands on **Reconciliation**

→ KEELLS SUPER KANDY → **Confirm** → Matched  
→ POS SALE CARD → **Confirm** → Matched  
→ CEB ELECTRICITY → **Confirm** → Matched  
→ BANK FEE → **Ignore** → Reason `Bank charge — no matching ledger transaction required for this demo.` → **Ignore line**

→ Metric **Reconciliation** = **100%**

→ **Reports** → **Run** → P&L **unchanged**

→ Left nav **Close** → Year **2026** · Month **September** → **Refresh** → Cedar Café → **Open workspace**

→ Blocker: **1 missing-document request is still open.**  
→ **Close period** is **disabled**. This is intentional.

→ Top right **Logout**

**LOGIN AMAYA**  
`amaya@cedarcafe.lk` / `DemoPass123!` → **Sign in**  
→ lands on **Your business hub**  
→ **September rent invoice** · Open  
→ **Choose file** → `demo/files/utility-bill.pdf` → **Upload file** → **Uploaded**

→ **Logout**

**LOGIN PRIYA** (repeat credentials)  
`priya@harborledger.demo` / `DemoPass123!` → **Sign in**

→ **Close** → 2026 / September → **Refresh** → **Open workspace**  
→ Request line shows **UPLOADED** → **Mark complete**  
→ Close note: `September 2026 demo close after reconciliation`  
→ **Close period** → **Closed**

→ Optional: **Expenses** → try 15 Sep / Utilities / 100 / Vendor `Should fail` → **Create draft**  
→ Expect: *This date belongs to a closed bookkeeping period…*

→ **Reports** → **Trends** (August vs September)  
→ Left nav **Audit log** → point at recent EXPENSE APPROVED, BANK IMPORT COMPLETED, RECONCILIATION CONFIRMED, BANK TRANSACTION IGNORED, DOCUMENT REQUEST UPLOADED, DOCUMENT REQUEST COMPLETED, PERIOD CLOSED

---

## 15-MINUTE CUT

Priya login → Documents Keells Accept as draft → Approve  
→ Income 18500 Card Approve  
→ Expenses CEB 6200 Approve  
→ Reports Run (18,500 / 11,050 / 7,450)  
→ Banking Import `bank-sept.csv` → Confirm ×3 → Ignore fee → 100%  
→ Close (show blocker)  
→ Logout → Amaya Upload file `utility-bill.pdf`  
→ Logout → Priya Mark complete → Close period  
→ Stop (skip Trends, Audit, closed-period write test unless asked)

---

## ⚠ DO NOT

- Open **4201** or a non–Finance Platform login  
- Seed mid-demo  
- Upload a second Keells file  
- Import `bank-sept.csv` twice  
- Click **Void**, **Reject document**, **Unmatch**, **Reopen period**, **Platform admin**  
- Close the period before showing the rent-request blocker  
- Continue to Banking if P&L is not 18,500 / 11,050 / 7,450

---

## IF IT BREAKS

| Symptom | Action |
|---|---|
| Duplicate import | **STOP.** Reset DB, re-seed, restart demo. |
| P&L wrong | Confirm three September lines are **APPROVED**. Do not import bank. |
| No Keells row | Documents Status → **All**. Do not upload another copy unless the file is truly missing. |
| No bank suggestions | Ledger was not approved before import. **STOP.** Reset. |
| Close still blocked after Mark complete | Confirm request status **COMPLETED**, then click **Refresh** on Close list and reopen workspace. |
