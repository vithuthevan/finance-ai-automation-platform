# Commercial product readiness

## 1. Product today

Multi-tenant accounting practice platform: firm onboarding, clients, document requests/uploads, AI extraction (optional provider), expense/income approval, bank import and reconciliation, month-end readiness/close, reporting, practice work queues, monthly evidence checklist, subscriptions/quotas, platform admin grants.

**Invoice-to-cash (this pass):** AR customers, sales invoices (draft → issue → void), payment recording, multi-invoice allocation, reversal, AR summary/ageing APIs, Angular AR hub (`/app/ar`), V34 migration separating document vs settlement status, client chase uses real `contactEmail` (suppressed when missing).

## 2. Target customers

- Accounting firms and bookkeepers managing many SME clients
- SME finance teams with accountant oversight (business owner upload-only mode)

## 3. Complete commercial workflows

- Firm registration/onboarding and client setup
- Document request → upload → AI extraction → review → draft expense/income → approve
- Bank CSV import → match suggestions → confirm reconciliation
- Month-end readiness → period close
- Practice work prioritization (queues + month-end command center + today strip)
- Subscription trial/limits (manual billing provider)
- **Core invoice-to-cash (API + basic UI):** customer → draft invoice → issue → record payment → allocate → outstanding zero

## 4. Partial workflows

- **Invoice-to-cash polish:** true PDF (HTML download today), payment allocation UI from invoice detail, credit notes, tenant isolation integration tests for AR
- **Client chase:** run enrollment + email dispatch; policy admin UI and evidence/monthly gaps as chase sources still incomplete
- **Advanced reconciliation:** invoice suggestions in scoring service; match group confirm UI and payment-linked groups incomplete
- **Practice assignments:** `practice_work_assignments` table; assignment/reassign APIs and work UI filters incomplete
- **Automation rules:** storage only
- **Integrations / Stripe:** schema only

## 5. External blockers

- SMTP for invoice/chase email (`APP_EMAIL_PROVIDER`)
- OpenAI or other LLM API key for production AI extraction
- Docker on developer machines for local Testcontainers (CI should run PostgreSQL tests)
- Stripe / accounting OAuth apps (not required for first pilot)

## 6. Security status

AR endpoints: `ADMIN`/`ACCOUNTANT` mutate; `AUDITOR` read; `BUSINESS_OWNER` no AR mutations. Firm ID from auth context only.

## 7. Multi-tenant status

Firm scoping in services; existing isolation tests; add AR-specific isolation tests (P0).

## 8. Reliability status

Invoice numbering via `firm_invoice_number_sequences` with pessimistic lock. Payment allocation uses row locks on payment/invoice. Idempotency headers supported on issue/allocate POSTs.

## 9. Production readiness

Configure email, run CI integration suite with Docker, Redis for multi-instance rate limits, pilot runbook walkthrough including `/app/ar`.

## 10. First customer checklist

1. Deploy PostgreSQL + app with strong JWT secret  
2. Configure SMTP  
3. Create firm staff and clients  
4. Walk document → close golden path  
5. Walk invoice-to-cash on `/app/ar`  
6. Verify chase emails use client `contactEmail` (not placeholders)  

## 11. Remaining P0/P1

**P0:** Payment allocation UI; chase policy UI; practice assignment APIs; AR tenant isolation tests; run full `platform-app` Testcontainers suite in CI  

**P1:** Match group UI; credit notes; bank confirm → auto payment; automation evaluator  
