# Pilot product scope — Finance Platform

**Audience:** Sales, founders, Customer #1 onboarding  
**Positioning (validated externally):** Month-end readiness and client evidence for small accounting firms — not a full GL replacement.  
**Evidence labels:** CODE-VERIFIED unless noted.

---

## SUPPORTED (safe to promise for Pilot #1)

| Capability | Notes |
|------------|--------|
| Firm self-registration + ADMIN user | `POST /api/v1/auth/register`; no JWT until login (README, `RegistrationService`) |
| Trial subscription (STARTER, 14 days default) | `FirmService` + `SubscriptionService.createDefaultForFirm` |
| Firm settings | Currency (default LKR), timezone (Asia/Colombo), FY start month, AI toggle (`docs/FIRM_SETTINGS.md`) |
| Clients CRUD + deactivate | Firm-scoped; assignment for non-admin users |
| Users & roles | ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER, UPLOAD_ONLY (`docs/SECURITY.md`) |
| Categories | Firm-scoped expense/income categories (manual create; **no default pack**) |
| Documents / evidence | Upload, review, link, reject; local or S3 storage |
| Draft → approve → void ledger | Income/expense; approved-only reporting |
| Bank accounts + CSV import | Mapping profiles per client; duplicate import prevention (tests) |
| Bank reconciliation | Match to expense/income, ignore lines, suggestions; **one ledger line per bank line** |
| Document requests | Create, owner upload, complete/cancel; blocks close when open |
| Month-end close | Readiness engine, blockers/warnings, close/reopen (ADMIN) |
| Practice work queue + portfolio | Close readiness across clients |
| P&L, income/expense summaries, trends | CSV/XLSX export; no PDF |
| Audit log | Firm/client scoped actions |
| In-app + email notifications | When SMTP configured |
| Subscription quotas & trial expiry | Platform can extend trial / set ACTIVE manually (`SAAS_SUBSCRIPTIONS.md`) |

---

## LIMITED (promise with constraints)

| Capability | Constraint |
|------------|------------|
| Currency | Single firm default; no FX conversion (`IMPLEMENTATION_STATUS.md`) |
| Periods | Calendar months only; FY start month stored but not used for close logic |
| Bank reconciliation | No split transactions; transfers/fees via match or ignore; no open-banking feeds |
| Business owner onboarding | Admin sets password at user create; **client assignment not in Users UI** — API `PUT .../client-access` (CODE-VERIFIED, `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md`) |
| AI extraction | Optional; images only for vision; PDF not true OCR; **recommend AI OFF for Pilot #1** (`docs/AI.md`) |
| Reporting | Cash movement label is recorded in/out, not IFRS cash flow; category hierarchy metadata only |
| Close “ready” | Warnings (e.g. unsupported approved) may remain at 100%; bank recon is **blocker** when bank account exists |
| Scale | Work-queue filters post-query; suitable for 5–50 clients per firm, not 1000 |
| Email | Production **requires** real SMTP (`ProductionEnvironmentValidator`, prod profile) |
| Billing | No Stripe; manual invoicing only |

---

## NOT SUPPORTED (do not sell as in-scope for pilot)

| Area | Status |
|------|--------|
| Double-entry GL, chart of accounts, journal | Not in schema (`IMPLEMENTATION_STATUS.md`) |
| Balance sheet, trial balance, general ledger report | Not implemented |
| AR/AP, invoicing, payments | Not implemented |
| VAT/sales tax engine, tax filing | Not implemented |
| Payroll | Not implemented |
| Xero / QuickBooks / Sage integration | **No code references** (CODE-VERIFIED grep) |
| Client or transaction bulk CSV import | Not implemented; clients created one-by-one |
| Open Banking / live bank feeds | Not implemented |
| Native mobile apps | Responsive web only |
| Enterprise SSO / SAML | Not implemented |
| MFA | Not implemented (`CRITICAL_CODE_REVIEW_FINDINGS.md` notes gap for platform admin) |
| PDF reports | Not implemented |
| Zero-login owner upload | Not implemented |
| WhatsApp / email intake | Not implemented |
| Malware scanning on upload | Not implemented |
| Automatic payment collection | `ManualBillingProvider` only |

---

## INTEGRATE LATER (validate demand during pilot)

- Xero/QBO read-only or CSV bridge (OPTION C in ledger coexistence doc)
- Default category packs / industry templates
- Users UI: client access assignment + invite-by-email link
- Recurring document request packs
- Bank feed connectors
- AI with firm DPA addendum
- PDF reporting
- PostgreSQL RLS as tenancy backstop (`ARCHITECTURE_SCHEMA_SECURITY.md`)

---

## Safe sales claims vs never claim

| Safe | Never |
|------|--------|
| “Evidence-to-close workflow with bank recon and month-end controls” | “Replaces Xero/QuickBooks as your GL” |
| “Approved P&L and exports for client reporting” | “Statutory accounts / audited financial statements” |
| “Client document requests with owner upload portal” | “Automatic tax filing” |
| “Practice portfolio: who is ready to close and why” | “Real-time bank feeds for all banks” |

**CUSTOMER-VALIDATION REQUIRED:** Whether firms will adopt OPTION B (close layer alongside Xero) vs OPTION A (working books in platform).
