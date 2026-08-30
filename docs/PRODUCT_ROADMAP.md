# Product Roadmap

## V1

Accountant-first Document-to-Close platform for bookkeeping firms managing multiple SME clients.

- Firm registration, JWT login, refresh tokens, password change/reset
- Users, roles, client access types (`FULL`, `READ_ONLY`, `UPLOAD_ONLY`)
- Client and category management
- Document upload, storage (local/S3), duplicate detection, download
- Optional AI extraction with manual fallback
- Accountant review that creates DRAFT transactions only
- Expense/income lifecycle: DRAFT → APPROVED → VOID
- P&L, comparison, dashboard, CSV/XLSX export
- CSV bank import and assisted reconciliation
- Document requests for missing evidence
- Month-end close with readiness blockers and controlled reopen
- In-app notifications, audit viewer, firm settings
- Manual SaaS plan/limits foundation
- Angular workspace for ADMIN, ACCOUNTANT, AUDITOR, BUSINESS_OWNER
- Independent frontend/backend deployables in one repository (same-origin or separate hosts)

## Future

- Official tax/IRD export packs when a specification exists
- Payment-gateway billing
- Direct bank connections
- Deeper chart of accounts / multi-currency conversion
- Localized Sinhala/Tamil UI translations
- PDF report packs
- Natural-language reporting (not a generic chatbot)

## Out of Scope

Payroll, HRM, inventory, POS, CRM, e-commerce, project management, loan origination, marketplace, social features, full ERP, consumer budgeting, cryptocurrency, trading, generic AI chat.
