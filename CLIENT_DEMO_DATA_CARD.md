# CLIENT DEMO DATA CARD

Emergency reference. Keep this visible during the meeting.

**Source of credentials:** `demo/seed_demo.py` (intentional local/demo only).  
**Do not use production credentials.**

---

## LOGIN 1 — Firm Administrator

| | |
|---|---|
| Name | Priya Fernando |
| Role | ADMIN (top bar shows **Admin**) |
| Email | `priya@harborledger.demo` |
| Password | `DemoPass123!` |
| After Sign in | Dashboard (`/app/dashboard`) |

## LOGIN 2 — Business Owner

| | |
|---|---|
| Name | Amaya Silva |
| Role | BUSINESS_OWNER (top bar shows **Business Owner**) |
| Email | `amaya@cedarcafe.lk` |
| Password | `DemoPass123!` |
| After Sign in | Your business hub (`/app/owner`) |

## LOGIN 3 — Accountant (NOT used in the main demo)

| | |
|---|---|
| Name | Nimal Perera |
| Role | ACCOUNTANT |
| Email | `nimal@harborledger.demo` |
| Password | `DemoPass123!` |

Use only if the client asks to see an accountant login. Priya can run the whole workflow.

---

## FIRM / CLIENT

| | |
|---|---|
| Firm | Harbor Ledger Partners |
| Client | Cedar Café (Pvt) Ltd |
| Currency | LKR |
| Timezone | Asia/Colombo |
| Period | September 2026 |

## BANK

| | |
|---|---|
| Bank | Commercial Bank |
| Account | Cedar Café Operating |
| Number | ****4521 |
| UI option | **Commercial Bank · Cedar Café Operating** |

## DOCUMENT (already seeded — do not re-upload)

| | |
|---|---|
| File | `demo/files/keells-receipt.pdf` |
| Inbox name | `keells-receipt.pdf` |
| Seed note | Keells supplies 5 Sep |
| Type | RECEIPT |
| Status before review | Needs review |

## KEELLS EXPENSE (create from Document review)

| Field | Value |
|---|---|
| Type | Expense |
| Date | 5 September 2026 |
| Category | EXP-FOOD Food & beverage supplies |
| Amount | 4850 |
| Tax | 0 (leave default) |
| Currency | LKR (leave default) |
| Party | Keells Super |
| Reference no. | LEAVE BLANK |
| Description | LEAVE BLANK |
| Button | **Accept as draft** |
| Then | Expenses → Status **Draft** → **Approve** |

## POS INCOME (create on Income page)

| Field | Value |
|---|---|
| Date | 8 September 2026 |
| Category | Café sales |
| Amount | 18500 |
| Customer | Card settlement |
| Payment | Card |
| Description | LEAVE BLANK |
| Button | **Create draft** → **Approve** |

## CEB EXPENSE (create on Expenses page)

| Field | Value |
|---|---|
| Date | 10 September 2026 |
| Category | Utilities |
| Amount | 6200 |
| Vendor | CEB |
| Description | LEAVE BLANK |
| Button | **Create draft** → **Approve** |

## P&L (September 2026, approved only)

| | |
|---|---|
| Total Income | **18,500.00** |
| Total Expenses | **11,050.00** (4,850 + 6,200) |
| NET PROFIT | **7,450.00** |

If these values are missing, **do not continue to Banking**.

## BANK CSV

| | |
|---|---|
| File | `demo/files/bank-sept.csv` |
| Use at | Banking → **Import** |
| Rows | 4 |

| Date | Description | Debit | Credit |
|---|---|---|---|
| 2026-09-05 | KEELLS SUPER KANDY | 4,850 | |
| 2026-09-08 | POS SALE CARD | | 18,500 |
| 2026-09-10 | CEB ELECTRICITY | 6,200 | |
| 2026-09-12 | BANK FEE | 250 | |

Reconciliation: **Confirm** / **Confirm** / **Confirm** / **Ignore**. Target **100%**.

## DOCUMENT REQUEST

| | |
|---|---|
| Title | September rent invoice |
| Owner upload file | `demo/files/utility-bill.pdf` |
| There is no dedicated rent PDF | use this existing demo file |
| Owner button | **Upload file** |
| After upload | **Uploaded** (not Completed) |
| Priya button | **Mark complete** |

## CLOSE

| | |
|---|---|
| Nav | **Close** |
| Year | 2026 |
| Month | September |
| First visit blocker | `1 missing-document request is still open.` |
| Close button | **Close period** (disabled until blockers clear) |
| Close note | `September 2026 demo close after reconciliation` |

## URLS

| | |
|---|---|
| Finance UI | http://localhost:4200 |
| Login | http://localhost:4200/login |
| Backend ready | http://localhost:8080/api/v1/health/ready |
| **Wrong** | http://localhost:4201 |
| **Wrong** | http://localhost:4200 if the Sign in card is not **Finance Platform** |

## DEMO FILES

| Path | When | What it is |
|---|---|---|
| `demo/files/keells-receipt.pdf` | Already in inbox | Keells receipt evidence |
| `demo/files/keells-receipt.png` | Only if PDF missing from inbox | Same receipt, image |
| `demo/files/bank-sept.csv` | Banking Import | September statement |
| `demo/files/utility-bill.pdf` | Amaya rent request | Stand-in for rent invoice |
