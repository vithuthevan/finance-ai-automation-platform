# Demo Q&A preparation

Concise answers grounded in this repository. Do not over-claim.

---

### What problem does this solve?

Accounting firms juggle client documents, draft transactions, bank statements, chase emails, and month-end checklists across tools. The platform centralizes **document → review → approve → reconcile → close → report** per client with firm-level visibility of what still blocks close.

### Who would use it?

- **ADMIN** — firm setup, users, categories, subscription  
- **ACCOUNTANT** — review, approve, bank, close (assigned clients)  
- **BUSINESS_OWNER** — upload requested documents, limited visibility  
- **AUDITOR** — read-only approved data, reports, audit log  

### Is this an ERP?

No. It is an **operational control layer** around bookkeeping: evidence, approvals, bank reconciliation, close readiness, and reporting—not a full GL, payroll, or inventory ERP.

### How does multi-tenancy work?

Every authenticated user belongs to a **firm** (`firmId` in security context). API and data access are scoped to that firm. Clients belong to the firm.

### How is client data isolated?

Authorization checks **firm membership** and **client access** (`ClientAccessService`, role + access type). Users without assignment do not see another client’s data.

### How is financial duplication prevented?

- **Approvals** — drafts vs approved states for income/expenses  
- **Idempotency** — `Idempotency-Key` on mutating API calls (replay-safe retries)  
- **Bank import** — fingerprinting / duplicate protection on bank lines (see `BankTransactionFingerprint`, integration tests)  
- **Closed periods** — writes rejected when period is closed  

### How does bank reconciliation work?

Import CSV (or create from bank), match lines to **approved** income/expenses, confirm matches, or ignore lines with a reason. Summary shows reconciliation %; unresolved items can block period close when configured.

### What happens when a period closes?

Period moves to **CLOSED**; new drafts in that period are rejected. Close requires **readiness** (e.g. open document requests, reconciliation) to pass server-side checks (`CloseReadinessService` / period close services).

### Where is AI used?

Optional **document extraction** when `APP_AI_ENABLED` and a provider are configured (`module-ai`). Demo runs with **AI off**; users enter draft fields manually on review.

### Does AI automatically modify accounts?

No. Extraction proposes fields; **Accept as draft** and **Approve** are human steps before ledger impact.

### What happens if the same request is submitted twice?

Clients can send an **Idempotency-Key** header; the server returns the prior result for the same key/fingerprint instead of double-posting (see `IdempotencyService`, `IdempotencyFilter`).

### How do you prevent duplicate bank transactions?

Import pipeline uses transaction fingerprints and repository constraints; integration tests cover duplicate import protection.

### Can this integrate with Sage/Xero/QuickBooks?

**Not as live sync today.** Position as: export/reporting and operational workflow alongside the ledger system of record; integrations are roadmap, not demo features.

### How would you scale it?

Single deployable **modular monolith** (`platform-app` JAR), PostgreSQL, optional object storage and AI providers. Horizontal scale = multiple app instances behind a load balancer with shared DB and storage (standard Spring Boot pattern).

### Why modular monolith instead of microservices?

Faster delivery, simpler ops for a small team, clear module boundaries (`module-auth`, `module-finance`, `module-reporting`, `module-ai`) without network sprawl for current scale.

### How would you monetize it?

Repository includes **subscription plans** (e.g. STARTER trial on registration, user limits). Commercial packaging is product/business—not fully exercised in demo.

### What is still unfinished?

Examples honest from code/docs: **Users UI client assignment** (API works; UI gap for owners), **staff workload API without UI**, deep **accounting integrations**, optional **AI** paths, some portfolio UX polish. Command center requires **current backend build** (stale Docker image may 404).

### What would you build next?

Client assignment in Users UI, stronger command-centre narrative on dashboard, reconciliation automation, pilot-hardening (ops/legal), integration connectors—prioritize from paid pilot feedback.
