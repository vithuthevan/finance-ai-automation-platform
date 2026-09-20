# Pilot ledger coexistence strategy

**Question:** Customer #1 already uses Xero, QuickBooks, Sage, or spreadsheets — what do we do **today**?  
**Do not build integrations in pilot.** Choose a operating model per client.

---

## What the platform actually is (CODE-VERIFIED)

- **Income + expense** ledger with categories, draft/approve/void, client scope
- **Not** double-entry; no sync to external COA
- Reports = approved aggregates + exports

---

## Options for Pilot #1

### OPTION A — Platform as working books (pilot subset)

**When:** Firm willing to run **5–15 simple SME clients** only in Finance Platform for a 90-day pilot; low transaction volume; CSV bank statements.

**Process:**

1. Create client + categories in platform
2. Enter or import-approved history **from cutover month only** (no full history required)
3. Bank CSV → reconcile → close → P&L from platform

**Risks:**

| Risk | Mitigation |
|------|------------|
| Duplicate data entry vs Xero | Limit to pilot clients only; DOCUMENTED |
| Partner expects one system of record | Written pilot scope: platform is SOR for pilot clients |
| Opening balances / prior year | Start from agreed cutover month; no balance sheet in product |

**Duplication risk:** HIGH if they keep parallel entry in Xero.

---

### OPTION B — Close + evidence layer; external ledger remains SOR (RECOMMENDED DEFAULT)

**When:** Firm keeps Xero/QBO as GL; uses platform for **document collection, requests, readiness, optional shadow books for recon check**.

**Process:**

1. Month-end: collect evidence and outstanding items in platform
2. Optional: enter **summary or key lines** in platform for recon/P&L preview — or skip ledger and use documents-only slice (limited reporting value)
3. Final statutory/management accounts still produced in Xero

**Risks:**

| Risk | Mitigation |
|------|------------|
| Two systems feel heavy | Pilot **one client** first; measure chase reduction |
| P&L in platform ≠ Xero | Do not promise matching totals unless they enter same approved data |
| Sales oversells “full books” | `PILOT_PRODUCT_SCOPE.md` |

**Duplication risk:** MEDIUM if they only use requests + close checklist without re-keying transactions.

**CUSTOMER-VALIDATION REQUIRED:** Is OPTION B valuable enough to pay for without OPTION A?

---

### OPTION C — Manual CSV bridge (supported today)

**When:** Firm exports transactions or summaries from Xero/QBO/spreadsheet.

**Supported today:**

- Bank statement CSV → `GenericBankStatementCsvImporter` + saved mapping profiles
- Manual expense/income create or document → draft → approve
- Report CSV/XLSX export for comparison to Xero

**Not supported:**

- Automated COA import
- Automated transaction import from Xero export (no generic ledger CSV importer)
- Bi-directional sync

**Process:**

1. Founder receives firm’s bank CSV (already used for recon)
2. Accountant creates categories aligned **by name** with Xero (manual)
3. Key current-month activity or import bank only and match

**Risks:** Reconciliation/accounting consistency if categories diverge — **founder validates totals** on first client.

---

## Spreadsheets-only firms

**Best fit for OPTION A.** CSV bank + manual/AP entry mirrors current process with better close controls.

---

## Decision matrix (pilot eligibility)

| Firm profile | Recommended option |
|--------------|-------------------|
| 2–8 staff, 5–15 simple clients, CSV banks | A or C |
| Heavy Xero user, unwilling to re-key | B (requests + close) or **defer pilot** |
| Needs consolidated group / multi-currency | **Reject** (see qualification) |
| Needs VAT return from system | **Reject** for pilot |

---

## What to test in customer interviews

1. “Would you re-key 20–40 lines/client/month for pilot clients?” (A vs B)
2. “Is document chasing alone worth a pilot fee?” (B)
3. “Who is system of record for pilot clients — us or Xero?”
4. “Cutover month and opening position expectations?”

---

## Pilot #1 recommendation (INFERRED)

- **Default:** OPTION B for Xero-heavy firms; OPTION A for spreadsheet-light bookkeeping firms.
- **Ledger coexistence risk to monitor:** Partner trust in “ready to close” when bank recon blocker applies but GL lives in Xero — align on **bank CSV in platform** as minimum for recon hero feature.

**Label:** STRATEGY — not CODE; options A/B/C capabilities CODE-VERIFIED as above.
