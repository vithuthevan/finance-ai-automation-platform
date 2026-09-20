# First paid pilot readiness report

**Audit type:** First paid pilot (Bucket B) — audit and planning only  
**Date:** 2026-09-20  
**Verdict:** **CONDITIONALLY READY — COMPLETE P0/P1 FIRST**  
**Application code changed during audit:** NO  
**Production deployed during audit:** NO  

---

## Executive summary

The repository is the **Finance Platform** (Angular 20, Spring Boot modular monolith `platform-app`, PostgreSQL + Flyway V29). Core **document-to-close** capabilities — evidence, draft/approve ledger, bank CSV import, reconciliation, document requests, month-end readiness/close, practice work queue, P&L exports — are **implemented and integration-tested** (`GoldenPathWorkflowIntegrationTest`, `TenantIsolationIntegrationTest`).

Customer #1 **can** be onboarded **founder-assisted** on a **properly hosted** environment **after** operational P0 items: **proven backup/restore**, **production SMTP**, **legal baseline for PII**, **tenant-isolation test run on Postgres**, and **subscription/trial ops** so the firm is not suspended at day 14.

The largest **product** gaps for self-serve pilot are **no default categories**, **empty post-login experience**, and **Users UI missing client-access assignment** (owners appear broken without API). The largest **commercial** gap is **no Xero/QBO integration** — mitigate with **OPTION B/C coexistence**, not a build sprint.

**Do not implement recommendations in this audit yet.**

---

## Pilot definition

- 1 accounting firm, 2–8 staff, 5 real SME clients initially  
- English UI; LKR OK; CSV banks; manual billing; weekly founder support  
- No enterprise SSO, open banking, full GL, payroll, or tax filing  

---

## Current product state

| Layer | State |
|-------|--------|
| A. Demo ready | Yes — with `seed_demo.py` or from-scratch script + API workaround |
| B. First paid pilot | **Conditional** — product core ready; ops/legal/activation gaps |
| C. Public production SaaS | No — monitoring, legal scale, billing automation, hard multi-tenant ops |

---

## Existing strengths (CODE-VERIFIED / TEST-VERIFIED)

1. **Month-end readiness + close** with explicit blockers (drafts, documents, requests, bank recon).  
2. **Practice work queue + portfolio** — “who is ready to close and why.”  
3. **Tenant-scoped security model** with integration tests (when Postgres runs).  
4. **Bank CSV → reconcile → close** golden path test.  
5. **Approved-only reporting** + CSV/XLSX exports.  
6. **Document evidence workflow** with durable storage keys and authorized download.  
7. **Subscription/quota** with manual platform admin controls for pilot billing.  
8. **Production fail-fast** for JWT, email, S3 config (`ProductionEnvironmentValidator`, `ProductionJwtSecretValidator`).

**Strongest pilot capability:** Month-end **readiness + portfolio/work queue** tied to **bank reconciliation blockers** and **document requests**.

---

## P0 blockers (8)

| ID | Blocker | Label |
|----|---------|-------|
| 1 | No **evidence of successful restore drill** on target architecture | DOCUMENTED gap in `deploy/backup/README.md` |
| 2 | **Automated DB + document backups** not inherent — must configure | EXTERNAL-INFRASTRUCTURE |
| 3 | **Production SMTP** required (`APP_EMAIL_PROVIDER=log` fails prod) | CODE-VERIFIED |
| 4 | **TLS + secrets + prod compose** not deployed by default | EXTERNAL-INFRASTRUCTURE |
| 5 | **Privacy/terms/pilot DPA** not in repository | LEGAL-REVIEW REQUIRED |
| 6 | **Postgres integration tests** must pass before real data (12 skipped without Docker per TEST_RESULTS) | TEST-VERIFIED gap |
| 7 | **AI posture** — default firm `aiEnabled=true`; must disable for pilot unless explicit agreement | CODE-VERIFIED |
| 8 | **Trial suspension at 14 days** unless ops extends/activates | CODE-VERIFIED |

**Biggest P0 risk:** **Data loss / inability to restore** customer documents and ledger (backup + restore not proven).

---

## P1 pilot requirements (14)

1. Users UI **client access** assignment  
2. **Default category template** (or standardized founder pack)  
3. Post-login **next steps** / checklist  
4. Platform admin **ACTIVE / extend-trial** procedure tested  
5. **STARTER user limit** (3) — plan bump for staff + owners  
6. **SMTP end-to-end** tests (reset, document request)  
7. **Monitoring**: health + backup failure alerts  
8. **Release/rollback** dry run on staging  
9. **PILOT_DATA_EXIT_PLAN** communicated to customer  
10. **Ledger coexistence** agreement (Xero/spreadsheet)  
11. **Bank CSV** mapping cheat sheet for customer banks  
12. Update **stale docs** (platform admin grants vs email allowlist; backup doc paths)  
13. **CI**: run Testcontainers suites on merge  
14. **Owner mobile browser** smoke on real devices  

**Biggest onboarding blocker:** **No categories + empty dashboard + owner client-access UI missing** (API workaround acceptable for Pilot #1 only).

**Biggest migration blocker:** **No bulk import + “we already use Xero”** — process/strategy, not CSV importer.

**Biggest ledger-coexistence risk:** **Dual entry** (OPTION A) without written SOR; P&L in platform ≠ Xero.

---

## P2 validation opportunities

- Xero/QBO connector demand  
- PDF reports  
- Recurring document request packs  
- Zero-login owner upload  
- WhatsApp reminders  
- MFA  
- Client CSV import  
- AI-assisted extraction with DPA  

---

## Registration & onboarding

| Question | Answer | Evidence |
|----------|--------|----------|
| Can firm register? | Yes | `POST /api/v1/auth/register` |
| What is created? | Firm, ADMIN user, STARTER TRIAL subscription | `RegistrationService`, `FirmService` |
| Default settings? | LKR, Asia/Colombo, FY month 4, AI on | `docs/FIRM_SETTINGS.md` |
| Categories? | **None** | CODE-VERIFIED |
| Login after register? | Manual login; verification off by default | FROM_SCRATCH doc |
| Empty state? | Dashboard/work queue zeros | CODE-VERIFIED |

**Wizard/checklist classification:** Onboarding wizard P2; static checklist P1; category template P1; founder runbook **PILOT MANUALLY** acceptable.

**60-minute Zoom test:** **Yes**, if founder creates categories + API client-access.

---

## Client setup

Create/edit/deactivate clients in UI. No seed dependency for real operation (`seed_demo.py` is demo-only).

---

## Team & business owner

| Capability | Status |
|------------|--------|
| ADMIN/ACCOUNTANT/AUDITOR/OWNER create | UI yes |
| Client access FULL/READ_ONLY/UPLOAD_ONLY | API yes; **UI no** |
| Invite with email link | No — password at create |
| Deactivate user | Yes |
| Password reset | Yes with SMTP |
| Revoke access | Via client-access replace |

**Pilot #1 without developer:** **Partial** — needs founder API for owner access until P1 UI.

---

## Migration

See `PILOT_MIGRATION_PLAN.md`. No unsafe SQL. 5 clients manual. Cutover month recommended.

---

## Ledger coexistence

See `PILOT_LEDGER_COEXISTENCE_STRATEGY.md`. **Pilot #1 recommendation:** OPTION B for Xero-heavy; OPTION A for spreadsheet-light.

---

## Accounting scope

See `PILOT_PRODUCT_SCOPE.md`. Integrity: draft vs approved, closed period guards, idempotency keys, reconciliation duplicate prevention — **CODE-VERIFIED** with tests (`FinancialLifecycleIntegrationTest`, golden path).

**Accounting-integrity status:** **Strong for scoped model**; not a GL.

---

## Documents

Local `/data/uploads` or S3; keys `firms/{firmId}/clients/{clientId}/documents/{uuid}`. Restart-safe on volumes. **Document-storage durability:** **Conditional on backup/S3** — not ready until P0 backup/restore.

---

## Banking & reconciliation

CSV import, mapping profiles, duplicate rejection, match/ignore/suggest, close blocker when bank account exists. **Bank-import readiness:** **Ready** for pilot CSV cases. Split transactions **not supported** — classify as pilot exclusion.

---

## Requests & close

Full loop with email when SMTP configured. Recurring packs **P2**.

**Period-close readiness:** **Ready** — hero capability.

---

## Reporting & audit

P&L, income, expense, trends; CSV/XLSX. No PDF. Audit firm/client scoped.

---

## Authentication & authorization

| Area | Status |
|------|--------|
| Password hashing, policy, rate limit | CODE-VERIFIED SECURITY.md |
| Refresh cookie + memory access token | CODE-VERIFIED |
| Tenant isolation | **TEST-VERIFIED** (when Docker runs) |
| Authorization | Service-layer RBAC CODE-VERIFIED |
| MFA | Not implemented — **P2 for pilot** |

---

## Email & notifications

In-app + email (async). Prod requires SMTP. **Real-email status:** **Not ready** until configured — **P0**.

---

## Observability

`/api/v1/health`, `/health/ready`. No bundled APM. **Monitoring/alerting:** **Minimal acceptable** with external uptime + backup alerts — **P1**.

---

## Deployment & release

`docker-compose.prod.yml`, VPS guide, backup scripts. **Deployment readiness:** **Templates ready**; **RUNTIME-VERIFIED** hosting not done. **Release/rollback:** Documented in VPS/DEPLOYMENT; needs one dry run — **P1**.

---

## Performance

Work-queue/portfolio designed for SME client counts. No pilot-scale perf blockers identified (INFERRED).

---

## Privacy & AI

No privacy policy in repo — **LEGAL-REVIEW P0**. AI optional; **recommend AI OFF** for Pilot #1 (`docs/AI.md`).

---

## Billing

Manual — `PILOT_BILLING_PROCESS.md`.

---

## Pilot qualification & metrics

**Good pilot:** 2–8 staff, 5–15 clients, CSV, simple books, close collaboration.  
**Reject:** payroll/tax/GL-only needs, 24/7 SLA, 1000 clients.

**Success metrics (measurable):**

- Time to first client & first document  
- Time to first bank recon & first close  
- % clients “ready to close” visible in portfolio  
- Owner upload response rate on requests  
- Support tickets / week  
- Willingness to pay after 90 days  

**Aha moment:** Portfolio/work queue: **“3 ready, 2 blocked — here’s exactly why.”**

---

## Demo value vs production/pilot value

| DEMO VALUE | PRODUCTION/PILOT VALUE |
|------------|------------------------|
| AI extraction demo | SMTP, backups, owner client access |
| Seeded Harbor Ledger story | Empty firm + real CSV banks |
| Polished empty states | Category template + legal |
| Mock AI filenames | Approved-only P&L correctness |
| Single perfect client | 5-client portfolio close matrix |

---

## Buckets summary

- **A — P0 before real data:** 8 items (ops, legal, restore, SMTP, test gate, AI/trial posture)  
- **B — P1 before/during pilot:** 14 items (activation UI, ops alerts, coexistence process)  
- **C — Validate during pilot:** Xero, pricing, PDF, recurring requests, WhatsApp, AI on  
- **D — Do not build:** Full GL, payroll, tax, open banking, Stripe (now), SSO, SOC2  

---

## Minimum pilot build

See `FIRST_PAID_PILOT_BUILD_PLAN.md` — **~2–4 weeks** calendar (ops + 1–2 eng sprints).  
**Sprint count:** 3 proposed.  
**Sprint 0 objective:** P0 safety (host, backup, restore proof, SMTP, legal drafts).

---

## Go-live checklist

`FIRST_PAID_PILOT_GO_LIVE_CHECKLIST.md`

---

## Final verdict

**CONDITIONALLY READY — COMPLETE P0/P1 FIRST**

Product workflow depth exceeds typical pre-revenue MVPs; **hosting, recovery proof, legal, and owner onboarding friction** gate the first paying firm.

---

## Deliverables created

| File | Created |
|------|---------|
| FIRST_PAID_PILOT_GAP_MATRIX.md | YES |
| PILOT_MIGRATION_PLAN.md | YES |
| PILOT_LEDGER_COEXISTENCE_STRATEGY.md | YES |
| PILOT_PRODUCT_SCOPE.md | YES |
| PILOT_DATA_EXIT_PLAN.md | YES |
| PILOT_BILLING_PROCESS.md | YES |
| PILOT_SUPPORT_RUNBOOK.md | YES |
| FIRST_CUSTOMER_ONBOARDING_RUNBOOK.md | YES |
| PRE_PILOT_DO_NOT_BUILD.md | YES |
| FIRST_PAID_PILOT_BUILD_PLAN.md | YES |
| FIRST_PAID_PILOT_GO_LIVE_CHECKLIST.md | YES |
| FIRST_PAID_PILOT_READINESS_REPORT.md | YES |

---

## Exact recommended next action

**Execute Sprint 0 (P0):** provision staging/production with `docker-compose.prod.yml`, configure SMTP and secrets, schedule backups, **complete a documented restore drill**, run `:platform-app:test` with Docker so `TenantIsolationIntegrationTest` passes, and obtain **legal review of a minimal privacy/pilot agreement** — **in parallel**, schedule a **founder dry-run** from empty DB using `FIRST_CUSTOMER_ONBOARDING_RUNBOOK.md` (including API client-access step).
