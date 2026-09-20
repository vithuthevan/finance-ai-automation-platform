# Product–market need audit — Finance Platform

**Date:** 2026-09-20  
**Scope:** Commercial viability for accounting/bookkeeping firms (not a build task)  
**Repository verified:** YES — Angular 20 frontend, Spring Boot modular monolith (`platform-app`), PostgreSQL + Flyway V1–V29  
**Code changed:** NO | **Database changed:** NO | **Deployed:** NO  

---

## 1. Executive verdict

| Question | Answer |
|----------|--------|
| Does a real problem exist for small firms? | **PLAUSIBLE** — month-end coordination, missing evidence, portfolio visibility are widely described in industry content; not proven for *this* product or ICP. |
| Does the product technically address part of it? | **YES (CODE-VERIFIED)** — evidence, requests, draft/approve ledger, bank CSV recon, readiness blockers, practice work queue/portfolio, close, P&L exports, audit. |
| Is it meaningfully better than today’s workarounds? | **MIXED** — strong *if* firm runs books in-platform (OPTION A); **WEAK / CUSTOMER VALIDATION REQUIRED** if firm keeps Xero/QBO as GL without integration. |
| Would customers change behaviour? | **UNKNOWN** — duplicate entry, owner login, and maintaining workflow state in a second system are adoption risks. |
| Would they pay and retain? | **NO EVIDENCE** — no paying customers, pilots, or interview corpus in repo. |

**Recommendation: B — PROBLEM IS PLAUSIBLE — CUSTOMER DISCOVERY REQUIRED BEFORE MORE BUILD**

Do not treat technical completeness as commercial proof. A controlled paid pilot is justified only after discovery de-risks OPTION B/C coexistence and document-chase value.

---

## 2. What the product actually is today

### Architecture (verified)

- **Frontend:** Angular 20 — routes: dashboard, work queue, clients, documents, expenses/income, banking, close, reports, users/categories/audit/firm, owner portal (`app.routes.ts`).
- **Backend:** Spring Boot modules — auth, finance (clients, ledger, bank, close, requests), AI (optional), reporting; single JAR.
- **Data:** PostgreSQL, Flyway migrations through V29.
- **Flow (as implemented):** Firm → clients → users (roles + client access) → documents → expenses/income (draft/approve/void) → banking (CSV) → reconciliation → document requests → period close (readiness) → reports → audit.

### CURRENT PRODUCT (one paragraph)

An **accountant-first, multi-tenant web app** where a firm manages SME clients through **document evidence**, a **simple income/expense ledger** (not double-entry GL), **bank statement CSV import and reconciliation**, **document requests** to business owners, and a **rules-based month-end readiness engine** that blocks close until drafts, open requests, document review backlog, and (when a bank account exists) unreconciled bank lines are cleared—surfaced via **practice work queue** and **client portfolio** views, with **P&L/summary exports** and an **audit log**.

### Snapshot table

| Dimension | Today |
|-----------|--------|
| **TARGET USER** | Staff at small bookkeeping/accounting firms (ADMIN/ACCOUNTANT primary); BUSINESS_OWNER for uploads/requests; AUDITOR read-only. |
| **CORE JOB** | Move a client from “documents and transactions in flight” to **approved period + closed month** with traceable evidence. |
| **CORE WORKFLOW** | Upload/link documents → create/approve transactions → import bank CSV → match/ignore → clear requests → pass readiness → close period → export reports. |
| **CORE OUTPUT** | Closed accounting period, approved P&L-style aggregates (CSV/XLSX), audit trail, portfolio “ready vs blocked” signals. |
| **SYSTEM OF RECORD (default in code)** | Platform ledger + evidence for data entered here — **not** a statutory GL. |
| **COMPLEMENTARY SYSTEMS (typical)** | Xero/QBO/Sage, Excel checklists, email/WhatsApp, Google Drive/OneDrive — **no Xero/QBO sync in code**. |
| **CURRENT VALUE PROPOSITION (honest)** | “Evidence-to-close with explicit blockers and firm-wide close visibility” — **not** “replace your accounting software.” |

---

## 3. Pain inventory vs product (summary)

Legend: **A** solves today | **B** partial | **C** does not | **D** may add work | **E** customer validation required

| Pain | Class | Notes |
|------|-------|--------|
| Missing receipts/invoices | B/E | Requests + owner portal help; no WhatsApp/email intake; chase may stay on WhatsApp. |
| Documents via WhatsApp/email | C/B | No ingestion; parallel channels likely remain. |
| Late documents | B/E | Requests + reminders (SMTP); discipline still required. |
| Client chasing | B/E | Structured requests vs informal channels — friction trade-off. |
| Unknown client readiness | **A/B** | Close readiness + portfolio/work queue **if** data maintained in platform. |
| Month-end spreadsheets | B/E | Replaces checklist **only if** blockers match firm’s real close definition. |
| Approval backlog | A/B | Work queue + draft blockers; requires ledger in platform. |
| Unreconciled bank lines | A/B | Strong **when** CSV recon done here; weak if Xero already reconciled feeds. |
| Bank CSV manipulation | A | Importer + mapping profiles (CODE-VERIFIED). |
| Partner status questions | B | Portfolio view exists; trust depends on workflow adoption. |
| Portfolio-wide view | A/B | Work page portfolio + close work queue API. |
| Staff workload visibility | B | Dashboard/work counts; not full capacity planning. |
| Scattered evidence | B | Central store; duplicate storage in Drive still likely. |
| Scattered client comms | C/B | Notifications email; no integrated inbox. |
| Audit evidence retrieval | B | Audit log + linked documents; not full engagement archive. |
| Repeated monthly requests | B | Manual per request; no recurring packs. |
| Close delays / rework | E | Outcome depends on adoption; not measured. |
| Duplicate data entry | **D** | High risk if Xero + platform ledger both maintained. |

---

## 4. Competitor / workaround reality

**Real competitors are often workarounds:** Excel close trackers, Karbon/Financial Cents/Jetpack (practice management + client tasks), Dext/Hubdoc/AutoEntry (capture → **publish to Xero**), Xero/QBO (ledger + bank feeds + reports), FloQast/ApprovalMax (close/approvals at higher tier).

| Alternative | What it does well vs us | What we might still offer |
|-------------|-------------------------|---------------------------|
| **Xero/QBO** | GL, bank feeds, statutory reporting ecosystem | Close checklist tied to **our** evidence/recon only if books live here or sync exists |
| **Dext + Xero** | Multi-channel capture, OCR, publish to GL, missing paperwork from recon | We lack publish-to-Xero and capture channels |
| **Karbon** | Month-end templates, client tasks, email, portfolio of **work** (not GL blockers) | Our blocker engine is more **ledger/recon-specific** — **if** books in platform |
| **Excel/WhatsApp** | Zero switching cost, clients already comply | We need **10× clarity** on “who is blocked and why” to justify change |

**EXTERNAL MARKET RESEARCH:** Karbon/Dext positioning summarized from public help/marketing pages (2025–2026); pricing not relied on. Sri Lanka-specific competitor landscape: **CUSTOMER VALIDATION / EXTERNAL RESEARCH REQUIRED.**

---

## 5. Five-person firm thought experiment (40 SME clients, Xero + Excel + WhatsApp)

**Why pay extra?**

| Capability | Pay vs workaround? | Classification |
|------------|----------------------|----------------|
| Document storage | Weak vs Drive | COMMODITY |
| P&L | Weak vs Xero if books there | COMMODITY / DUPLICATIVE |
| Bank recon | Weak vs Xero feeds unless CSV-only clients | SUPPORTING or DIFFERENTIATING (segment-dependent) |
| Client messaging | Weak vs WhatsApp unless portal adopted | SUPPORTING |
| Reports | Weak vs Xero | COMMODITY |
| Work queue | Moderate vs Excel if unified blockers trustworthy | **POTENTIAL DIFFERENTIATOR** |
| Month-end readiness | **Strongest hypothesis** — if blockers = firm’s truth | **DIFFERENTIATING (conditional)** |

**POTENTIAL BUYING TRIGGERS (PLAUSIBLE, unvalidated):** growth 10→50 clients; partner losing visibility; month-end slipping; audit evidence hunt; distributed team; spreadsheet tracker breaking.

---

## 6. Duplicate work map (critical)

| Task | Current accounting system | Finance Platform | Duplicate? | Different numbers risk? | Benefit despite duplication? | Likely acceptable? |
|------|---------------------------|------------------|------------|-------------------------|------------------------------|-------------------|
| Expenses | Xero bills/expenses | Draft/approve expenses | YES if both | YES | Evidence linking | **E — validation** |
| Income | Xero | Income ledger | YES | YES | Same | **E** |
| Categories | Xero COA | Firm categories (manual) | YES | YES | None without mapping | **Unlikely** for Xero-heavy |
| Bank recon | Xero bank feed | CSV import + match | YES | YES | Second opinion / evidence tie-out | **E** — spreadsheet firms maybe |
| Reports | Xero | P&L export | YES | YES | Client-facing draft | **Weak** |

**Conclusion:** Commercial path for Xero-heavy firms is **OPTION B** (requests + readiness without full re-key) or **integration later** — not OPTION A at scale.

---

## 7. Strategic models

### MODEL A — Platform becomes bookkeeping system

| Dimension | Assessment |
|-----------|------------|
| Behaviour change | High — move posting from Xero |
| Product fit | **Best match to current code** |
| Migration | Manual categories, no bulk import, no opening BS |
| Duplicate-entry risk | Low *inside* platform; high vs client expectations |
| Sales | Easier story (“one system”) harder vs “we already have Xero” |
| Differentiation | Moderate — not full GL |
| Retention | Tied to data lock-in in platform |
| Engineering | Lowest incremental |

**Validation:** Will a firm designate platform SOR for 5–15 simple clients?

### MODEL B — Xero/QBO remains GL; platform owns evidence, requests, workflow, readiness

| Dimension | Assessment |
|-----------|------------|
| Behaviour change | Medium — second system |
| Product fit | **Partial** — readiness blockers assume ledger/bank **in platform**; documents-only slice gives **limited** readiness |
| Integration | None today — **gap** |
| Duplicate-entry risk | Low if **no** transaction entry; readiness may under-report Xero blockers |
| Sales | “Close control layer” — must prove value without P&L parity |
| Differentiation | Portfolio readiness **if** aligned with operational truth |
| Engineering | Needs product repositioning + optional Xero-agnostic checklist |

**Validation:** Is document chase + close checklist alone worth a fee?

### MODEL C — Hybrid / manual CSV bridge

| Dimension | Assessment |
|-----------|------------|
| Behaviour change | Medium-high |
| Fit | Bank CSV + optional shadow books |
| Risk | Category drift vs Xero |

**Validation:** Same as A/B per client cohort.

**System-of-record recommendation to validate:** Per pilot client, **written** SOR (platform vs Xero) before onboarding.

---

## 8. Hypothesis challenge: “Who is ready to close and what is blocking?”

### CODE-ANSWERABLE (YES, when data exists in platform)

`CloseReadinessService` evaluates checks: draft transactions, document review, unlinked docs, open document requests, bank recon (when bank account exists), warnings for unsupported approved.

Practice **work queue** + **portfolio** (`work.page.ts`): per client — review count, bank recon %, ready vs close status.

Can produce: Client A READY; B blocked (open requests); C blocked (unmatched bank); D blocked (drafts) — **for current calendar month period when accountants maintain data here**.

### CUSTOMER VALIDATION REQUIRED

- Is this harder than their Excel/Karbon tracker?
- Do partners open it daily?
- Will accountants keep platform current while Xero is SOR?
- Does “ready” match partner’s definition (tax, payroll, management adjustments)?
- Is 100% readiness with warnings acceptable?

**Hypothesis strength:** **PLAUSIBLE** as differentiated **if** OPTION A or bank+evidence discipline; **WEAK** for pure Xero shops without integration.

---

## 9. Document request loop vs WhatsApp

**Implemented:** Create request → notify (in-app + email if SMTP) → owner login → upload → accountant complete → open requests block close.

**Friction (CODE / UX):** Login required; admin-set passwords; client access via API not Users UI; no zero-login upload; no WhatsApp/email intake; no recurring packs.

**Adoption bottleneck risk:** **HIGH (E)** — business owners may ignore portal; accountants may still chase on WhatsApp.

---

## 10. Bank reconciliation hypothesis

**Adds value when:** spreadsheet-led firms; clients without software; banks without feeds; firm wants evidence-to-bank tie-out before close.

**Weak when:** Xero already reconciled — duplicate effort.

**Strength:** **PLAUSIBLE** in emerging-market CSV workflows; **WEAK** as universal differentiator.

---

## 11. AI commercial importance

Optional vision extraction → draft only; PDF limitations; firm `aiEnabled` default true (pilot risk).

| Role | Assessment |
|------|------------|
| vs Dext/AutoEntry | Behind on channels + publish |
| Trust | **E** |
| Commercial | **SUPPORTING / PREMIUM** at best; **NOT CORE PMF** until validated |

---

## 12. Personas

| Persona | Pain | Platform | Champion purchase? | Blocker? |
|---------|------|----------|----------------------|----------|
| **Partner** | Visibility, close timing, risk | Portfolio, close | Possible buyer | If ops fail |
| **Practice manager** | Work allocation, bottlenecks | Work queue | Possible champion | If duplicate work |
| **Accountant** | Data entry, chase, recon | Daily UI | Unlikely buyer | **Can block** if extra entry |
| **Business owner** | Hassle | Owner portal | No | **Can block** if won’t log in |
| **Auditor** | Evidence | Audit read-only | Influencer | Rare blocker |

**Economic buyer (hypothesis):** Partner / firm owner. **Daily user:** Accountant. **End client:** SME owner.

---

## 13. Feature commercial classification

| Feature | Class |
|---------|--------|
| Month-end readiness + blockers | **CORE VALUE** (conditional) |
| Practice work queue + portfolio | **CORE VALUE** (conditional) |
| Document requests + owner portal | **SUPPORTING** → core if chase is wedge |
| Document evidence + link to transactions | **SUPPORTING** |
| Bank CSV + recon | **SUPPORTING** / differentiating (segment) |
| Income/expense ledger + approve | **CORE** for A; **POSSIBLE DUPLICATION** for B |
| P&L / exports | **TABLE STAKES** / commodity vs Xero |
| AI extraction | **LOW COMMERCIAL VALUE** initially |
| Subscription/quota | **TABLE STAKES** for SaaS |
| Audit log | **SUPPORTING** |
| Full GL / tax / payroll | **NOT BUILT** — defer |

**Adoption friction features:** manual categories (no defaults), empty dashboard, dual ledger with Xero, owner onboarding friction.

---

## 14. Three commercially distinct directions

### DIRECTION 1 — “Simple firm books + close” (OPTION A)

- **WHO:** 2–8 staff, spreadsheet or light Xero, 5–20 simple clients  
- **PAIN:** No single close control; CSV banks  
- **SOR:** Platform  
- **REMOVE/DEEMPHASIZE:** Competing with Xero on tax/GL  
- **INTEGRATIONS:** Defer Xero  
- **VALIDATION:** Will they migrate clients off Excel/Xero subset?

### DIRECTION 2 — “Month-end operating layer” (OPTION B refined)

- **WHO:** Xero/QBO firms  
- **PAIN:** Chase + partner visibility  
- **SOR:** Xero; platform = evidence + requests + **checklist** (may need Xero-agnostic blockers)  
- **REMOVE/DEEMPHASIZE:** Mandatory ledger/recon for close  
- **INTEGRATIONS:** Read-only Xero status or manual checklist items  
- **VALIDATION:** Pay without transaction entry?

### DIRECTION 3 — “Client evidence + request specialist”

- **WHO:** Firms drowning in WhatsApp docs  
- **PAIN:** Missing paperwork  
- **SOR:** External GL  
- **CORE:** Requests, owner upload, document inbox, light tie to periods  
- **REMOVE/DEEMPHASIZE:** Bank recon as close gate  
- **VALIDATION:** Portal vs WhatsApp; zero-login need

**Recommended direction to test first:** **DIRECTION 2 for Xero-heavy prospects; DIRECTION 1 for spreadsheet-first prospects** — same codebase, different pilot contract and success metrics.

---

## 15. Pricing / value metrics (hypotheses only)

| Metric | Value alignment | Anxiety |
|--------|-----------------|--------|
| Per firm flat + active clients | Grows with portfolio value | Predictable |
| Per active client/month | Aligns with close | Counting disputes |
| Per close | Outcome-based | Hard to attribute |
| Per user | Easy | Punishes collaboration |

**Rational payment requires measurable:** accountant-hours saved on chase/coordination, days shaved off close, clients per accountant — **numbers from pilots, not invented.**

---

## 16. Geography

| Market | Hypothesis |
|--------|------------|
| **Sri Lanka (first)** | **PLAUSIBLE** — LKR default, Colombo TZ, CSV banks, local support; cloud adoption and firm size **E**. |
| UK/AU/NZ/UAE/SG | Later; need integrations, trust, compliance narrative **E**. |

---

## 17. Evidence inventory

### We HAVE

- Working document-to-close codebase and integration tests (per repo docs)  
- Explicit pilot scope and ledger coexistence strategy  
- Readiness engine tied to concrete blockers  
- Honest “no Xero” documentation  

### We DO NOT HAVE

- Customer interviews recorded in repo  
- Paid pilots or retention data  
- Proof OPTION B beats Excel/Karbon  
- Owner portal adoption rates  
- Willingness-to-pay quotes  
- Sri Lanka market sizing  

---

## 18. BUILD NOW vs VALIDATE (commercial lens)

**BUILD NOW only if blocking discovery/safe pilot/measurement:**

- Founder-assisted pilot ops (SMTP, backup, legal) — **ops not code**  
- Docs note P1: default categories, Users UI client access — **small activation** (user asked no code in this task; listed for awareness)  

**VALIDATE FIRST:** Xero coexistence model, document chase value, owner participation, pricing, AI, CSV recon segment, Karbon overlap.

**DEFER:** GL, payroll, tax, open banking, WhatsApp API, mobile native, enterprise SSO, large AI programme.

**DO NOT BUILD (until evidence):** Full GL, bi-directional Xero sync, generic workflow engine, SOC2 programme.

---

## 19. Kill criteria vs invest criteria

**CHANGE DIRECTION if:** firms satisfied with Karbon+Dext; accountants refuse any duplicate entry; partners don’t care about portfolio readiness; clients won’t use portal; no time savings; no paid pilot after demo.

**INVEST MORE if:** repeated unprompted pain stories; existing close spreadsheets; partners ask for portfolio view; pilot requests; real data upload; colleagues introduced; paid pilot; renewal.

---

## 20. Final recommendation

**B — PROBLEM IS PLAUSIBLE — CUSTOMER DISCOVERY REQUIRED BEFORE MORE BUILD**

The platform **can** deliver a credible “blocked vs ready” portfolio **for data maintained inside it**. That is **not** proof that firms will pay, change behaviour, or choose it over Excel + Xero + WhatsApp. The cheapest path to truth is **10–20 structured interviews** and **one paid, narrowly scoped pilot** with explicit OPTION A or B per client.

**Exact next action:** Schedule 3 firm-owner/practice-manager interviews using `CUSTOMER_DISCOVERY_PLAN.md`; do not expand product surface until OPTION B value or OPTION A migration willingness is observed.
