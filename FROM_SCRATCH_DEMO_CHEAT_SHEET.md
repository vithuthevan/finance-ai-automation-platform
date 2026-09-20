# From-scratch demo — cheat sheet

**Open:** `http://localhost:4200/login`

**Reset first:** see `FROM_SCRATCH_DEMO_RESET_GUIDE.md` (empty DB, no `seed_demo.py`).

---

## Flow (actual UI labels)

```
Register a firm
  → Create firm
  → Sign in (NOT auto-login)

Sign in
  → Dashboard (empty practice)

Clients → Create
  → Green Leaf Café (Pvt) Ltd

Categories → Create category (×5 minimum)

Users → Create user
  → Maya Fernando · Business owner · DemoPass123!

★ API: PUT …/users/{id}/client-access  UPLOAD_ONLY → Green Leaf
  (NOT on Users page — mandatory for owner login)

Documents → Upload → keells-receipt.pdf
  → Open row → Document review
  → Create draft manually → Expenses → Approve

Income → Create draft → Approve
  → 8 Sep · Café sales · 18500 · Card settlement · Card

Expenses → Create draft → Approve
  → 10 Sep · Utilities · 6200 · CEB

Reports → Profit & Loss
  → Sep 2026 · Run
  → 18,500 / 11,050 / 7,450

Banking → Accounts → Save account
  → Import tab · bank-sept.csv · Preview · Import
  → Reconcile tab · Confirm ×3 · Ignore bank fee

Month-end close → Sep 2026 → Open workspace
  → Request missing document (rent)
  → Close period DISABLED (open request)

Sign out → Sign in as maya@greenleaf-demo.example.com
  → Upload utility-bill.pdf on request

Sign in as Daniel → Mark complete on request
  → Close period → Close period

Expenses → post-close write → BLOCKED

Reports → P&L (same totals)

Audit → filter Green Leaf / September
```

---

## Numbers to remember

| Item | Value |
| --- | --- |
| P&L income | 18,500 |
| P&L expenses | 11,050 |
| Net profit | 7,450 |
| Bank match | 3 |
| Bank ignore | 1 |
| Reconciliation | 100% |

---

## Passwords

| User | Email | Password |
| --- | --- | --- |
| Admin | daniel.perera@summit-demo.example.com | DemoPass123! |
| Owner | maya@greenleaf-demo.example.com | DemoPass123! |

---

## Short vs full

| Version | ~Duration | Skip |
| --- | --- | --- |
| **Short** | 20 min | Firm settings tab, optional firm narrative on dashboard, Trends, comparison export |
| **Full** | 35–45 min | Nothing critical; include API client-access prep before show |

**Never skip:** Register → login → client → categories → Keells → approvals → P&L → bank → request → owner → close.

---

## If something breaks

| Symptom | Likely cause |
| --- | --- |
| Firm name exists | DB not reset or reuse name |
| Owner sees no requests / empty | Client access not assigned (API step) |
| Close still blocked after upload | Request still OPEN/UPLOADED — admin must **Mark complete** |
| Close blocked on bank | Unmatched lines — finish Reconcile tab |
| P&L wrong | Draft not approved or wrong client/dates |
| Login after register fails | Email verification enabled — check logs / disable for local |

---

## Presenter one-liners

- Registration: *“We start exactly where a new accounting firm would start.”*
- After client: *“The firm now has its first business client.”*
- After upload: *“Now we move from setup into daily bookkeeping.”*
- After approve: *“Only reviewed and approved transactions affect reporting.”*
- After bank: *“We validate the books against what moved through the bank.”*
- Close blocked: *“The system won’t finish the month while required work is outstanding.”*
- Owner upload: *“The client can supply evidence without the accountant’s admin controls.”*
- Close: *“Once controls pass, the period can be finalized.”*
- Audit: *“Everything we did leaves a trace.”*
