# From-scratch demo — data card (emergency reference)

**Story:** Summit Accounting Partners → first client Green Leaf Café → September 2026 month-end.

**Password (all demo users):** `DemoPass123!`

---

## New firm (registration)

| Field | Value |
| --- | --- |
| Firm name | `Summit Accounting Partners` |
| Your name | `Daniel Perera` |
| Email | `daniel.perera@summit-demo.example.com` |
| Password | `DemoPass123!` |
| Confirm password | `DemoPass123!` |

*Registration form has **no** currency, timezone, financial year, or registration number fields.*

---

## Admin login

| Field | Value |
| --- | --- |
| Email | `daniel.perera@summit-demo.example.com` |
| Password | `DemoPass123!` |

---

## Firm settings (optional, after login)

| Field | Value |
| --- | --- |
| Currency | `LKR` |
| Timezone | `Asia/Colombo` |
| Financial year start month | `April` (4) |

Path: **Firm settings** (`/app/firm`) → **Save** on General tab.

---

## Client

| Field | Value |
| --- | --- |
| Name | `Green Leaf Café (Pvt) Ltd` |
| Email | `accounts@greenleaf-demo.example.com` |

---

## Categories (create if list empty)

| Code | Name | Type |
| --- | --- | --- |
| `GL-FOOD` | Food & beverage supplies | EXPENSE |
| `GL-UTIL` | Utilities | EXPENSE |
| `GL-BANK` | Bank charges | EXPENSE |
| `GL-RENT` | Rent | EXPENSE |
| `GL-SALES` | Café sales | INCOME |

---

## Business owner

| Field | Value |
| --- | --- |
| Name | `Maya Fernando` |
| Email | `maya@greenleaf-demo.example.com` |
| Password | `DemoPass123!` |
| Confirm password | `DemoPass123!` |
| Role | Business owner |

**Client access:** not on Users form — assign via API (see main guide Step 8B).

---

## Document upload (Keells)

| Field | Value |
| --- | --- |
| Client | Green Leaf Café (Pvt) Ltd |
| Document type | Receipt |
| Note | `Keells supplies September` |
| File | `demo/files/keells-receipt.pdf` |

---

## Expense — Keells (from document review or Expenses)

| Field | Value |
| --- | --- |
| Date | 5 September 2026 |
| Category | Food & beverage supplies |
| Amount | `4850` |
| Vendor | `Keells Super` |
| Currency | LKR |

---

## Income — card settlement

| Field | Value |
| --- | --- |
| Date | 8 September 2026 |
| Category | Café sales |
| Amount | `18500` |
| Customer | `Card settlement` |
| Payment | Card |

---

## Expense — CEB

| Field | Value |
| --- | --- |
| Date | 10 September 2026 |
| Category | Utilities |
| Amount | `6200` |
| Vendor | `CEB` |

---

## P&L (September 2026)

| Metric | Expected |
| --- | --- |
| Client | Green Leaf Café (Pvt) Ltd |
| From | 2026-09-01 |
| To | 2026-09-30 |
| Total income | 18,500 |
| Total expenses | 11,050 |
| Net profit | 7,450 |

---

## Bank account

| Field | Value |
| --- | --- |
| Bank | `Commercial Bank` |
| Account name | `Green Leaf Café Operating` |
| Account no. (masked) | `****4521` |
| Currency | `LKR` |

---

## Bank CSV import

**File:** `demo/files/bank-sept.csv`

**Default column mapping (0-based):** Date=0, Description=1, Reference=2, Debit=3, Credit=4, Balance=5

| Date | Description | Debit | Credit |
| --- | --- | --- | --- |
| 2026-09-05 | KEELLS SUPER KANDY | 4850 | |
| 2026-09-08 | POS SALE CARD | | 18500 |
| 2026-09-10 | CEB ELECTRICITY | 6200 | |
| 2026-09-12 | BANK FEE | 250 | |

**Reconciliation targets**

| Bank line | Match to |
| --- | --- |
| Keells 4,850 | Keells expense 4,850 |
| POS 18,500 | Card settlement income 18,500 |
| CEB 6,200 | CEB expense 6,200 |
| Bank fee 250 | Ignore (e.g. `Bank service charge — no ledger entry`) |

**Expected summary:** Matched 3 · Ignored 1 · Unmatched 0 · Resolved 100%

---

## Document request (September close workspace)

| Field | Value |
| --- | --- |
| What you need | `Please upload the September shop rent invoice.` |
| Type | Purchase invoice |
| Due | 20 September 2026 |

**Owner upload file:** `demo/files/utility-bill.pdf` (demo evidence for rent)

---

## Closed-period test write

| Field | Value |
| --- | --- |
| Date | 15 September 2026 |
| Category | Utilities |
| Amount | `100` |
| Vendor | `Test Vendor` |

**Expected:** rejected — period closed (`PERIOD_CLOSED` / error toast); no new approved row.

---

## API — assign owner client access (required today)

After creating Maya, `PUT /api/v1/users/{userId}/client-access`:

```json
{
  "assignments": [
    {
      "clientId": "<Green Leaf client UUID>",
      "accessType": "UPLOAD_ONLY"
    }
  ]
}
```

Get `userId` / `clientId` from Network tab on **Users** / **Clients** list, or Swagger `GET /api/v1/users` / `GET /api/v1/clients` with bearer token from `POST /api/v1/auth/login`.
