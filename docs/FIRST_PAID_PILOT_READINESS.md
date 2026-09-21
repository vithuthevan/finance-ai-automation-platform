# First paid pilot readiness

**Overall status: CONDITIONAL READY**

## READY

- Firm multi-tenancy, users/roles, JWT, rate limiting, session revocation
- Document upload, AI extraction, ledger approval, bank CSV import, reconciliation (expense/income)
- Accounting periods, close readiness, reporting, subscriptions, outbox, practice queues (read)
- Invoice-to-cash backend (customers, issue, payments, allocate, reverse payment/allocation)
- **Payment allocation UI** (`/app/ar/payments/:id`) with multi-invoice wizard, partial/overpayment rules, reversal flows
- **Invoice PDF** (`GET /api/v1/ar/invoices/{id}/pdf`) via OpenHTMLToPDF (LGPL — see commercial notes below)
- **Invoice email** attaches PDF when SMTP/log provider configured
- **Bank → invoice payment** (`confirm-invoice-payment`) creates AR payment + allocations + match group
- **Client chase** policy API + practice chase list UI
- **Practice work assignment** APIs (assign, clear, complete)
- AR tenant isolation integration tests (baseline)

## CONDITIONAL

- **CI integration suite** must run with Docker/Testcontainers on merge (local dev may lack Docker)
- **Client chase enrollment** still primarily document requests; monthly evidence/close blockers need further enrollment wiring
- **Practice workspace** filters (team/at-risk views) partially covered by existing work queue; assignment UI not on every work type
- **Bank match UI** shows invoice suggestions; multi-invoice manual split UI is API-ready but not a dedicated wizard
- **Playwright E2E** invoice-to-cash flow still smoke-level; extend for full allocate path
- **Firm chase policy** admin tab — use `PUT /api/v1/client-chase/policy` or extend Firm settings UI

## BLOCKED (explicitly out of pilot scope)

- Stripe, Xero, MFA, RLS, Kafka, Kubernetes, advanced AI assistant

## Pilot release gate checklist

| Gate | Status |
|------|--------|
| Core document-to-close in UI | Conditional |
| Invoice-to-cash in UI | Ready |
| Payment allocation in UI | Ready |
| Bank payment → payment + allocation in UI | Conditional (single-invoice confirm) |
| Client chase configurable | Conditional (API + partial UI) |
| Practice assignments | Conditional (API) |
| Invoice PDF | Ready |
| SMTP configurable | Ready (existing app.email.*) |
| Tenant isolation tests | Conditional (AR added) |
| Integration suite green in CI | Required |
| Frontend test/build green | Required |

## OpenHTMLToPDF licensing

Library: `com.openhtmltopdf:openhtmltopdf-pdfbox` (LGPL 2.1). For commercial redistribution, review LGPL obligations (source offer, linking boundaries) with counsel. Acceptable for many SaaS deployments when used as a separate library dependency.

## Deployment requirements

- PostgreSQL with Flyway through **V35**
- `app.email.provider=smtp` + host/credentials for production invoice/chase mail
- Docker for full `platform-app` integration tests in CI
