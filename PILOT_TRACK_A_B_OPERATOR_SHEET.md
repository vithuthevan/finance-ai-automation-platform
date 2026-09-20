# Finance Platform — Customer #1 Operating Sheet

**Last aligned to repo evidence:** 2026-09-20 · **Refs:** `FIRST_PAID_PILOT_READINESS_REPORT.md`, `FIRST_PAID_PILOT_GO_LIVE_CHECKLIST.md`, `FIRST_PAID_PILOT_GAP_MATRIX.md`, pilot runbooks, demo guides, `docs/VPS_HOSTING.md`, `deploy/backup/README.md`

---

## CURRENT VERDICT

**CONDITIONALLY READY — COMPLETE P0/P1 FIRST**

## CURRENT OBJECTIVE

Get **one real accounting firm** to successfully **close real SME client work** through the platform and **choose to continue paying**.

## PRODUCT POSITIONING

Month-end readiness and **client evidence / workflow** platform for **small accounting firms** — not a full GL replacement.

## PRIMARY PILOT ICP

| Attribute | Target |
|-----------|--------|
| Firm size | **2–8 staff** |
| Clients | **5–15** simple SME clients (start with **~3**) |
| Language | English UI acceptable |
| Banking | **CSV bank statements** acceptable |
| Onboarding | **Founder-assisted** acceptable |

---

## HERO LINE (demo + pilot)

> **“Which clients are ready to close — and exactly what is blocking the others?”**  
> Reach this in **Portfolio / Work queue** as fast as practical. (`FIRST_PAID_PILOT_READINESS_REPORT.md`)

---

## TRACK A + TRACK B = REAL DATA GO-LIVE

| Track | Meaning | If missing |
|-------|---------|------------|
| **TRACK A** | Pilot customer **committed** (scope, fee, coexistence agreed) | No production data — keep validating |
| **TRACK B** | **P0 safety gates** passed (below) | **Demo/staging only — NO real customer financial data** |

**Both tracks required** for Customer #1 production data. Track B without Track A → **do not build speculative features**; keep selling/learning.

---

## DAILY SALES & DISCOVERY CHECKLIST

- [ ] Identify accounting firm  
- [ ] Qualify against **GOOD / NOT** criteria (below)  
- [ ] Conduct discovery conversation  
- [ ] Identify current **system of record:**  
  - [ ] Xero · [ ] QuickBooks · [ ] Sage · [ ] Excel · [ ] Other  
- [ ] Ask how they collect **missing documents**  
- [ ] Ask how they know **which clients are ready to close**  
- [ ] Ask how **bank reconciliation** works today  
- [ ] Ask what still happens in **Excel**  
- [ ] Ask what still happens via **WhatsApp / email**  
- [ ] Ask whether they would **maintain books in another system**  
- [ ] Show **10–20 min** product demo (`FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md`, `CLIENT_DEMO_CHEAT_SHEET.md`)  
- [ ] Show **AHA:** ready vs blocked clients + reasons (portfolio/work queue)  
- [ ] Determine **ledger coexistence** (`PILOT_LEDGER_COEXISTENCE_STRATEGY.md`):  
  - [ ] **OPTION A** — Finance Platform working books (pilot subset)  
  - [ ] **OPTION B** — External ledger remains SOR (evidence + close layer) — *default for Xero-heavy*  
  - [ ] **OPTION C** — Controlled CSV bridge  
- [ ] Determine pilot interest · Discuss **scope** · Discuss **fee** (`PILOT_BILLING_PROCESS.md`)  
- [ ] Obtain **pilot commitment**

### Before accepting pilot — record (commercial learning)

| Field | Notes |
|-------|--------|
| **SYSTEM OF RECORD:** | |
| **COEXISTENCE OPTION:** | A / B / C |
| **DUPLICATION RISK:** | HIGH / MEDIUM / LOW |
| **INTEGRATION REQUEST:** | |
| **CUSTOMER RESPONSE:** | Would evidence + chase + close readiness alone be worth paying for? |

**Questions to ask:**

1. What is your current **accounting system of record**?  
2. Would your team enter financial transactions into Finance Platform as well?  
3. If not, what information must move between Finance Platform and your existing ledger?  
4. Would **evidence collection + client chasing + close readiness alone** solve a problem worth paying for?

---

## GOOD CUSTOMER #1 · NOT CUSTOMER #1

| **GOOD** | **NOT CUSTOMER #1** |
|----------|---------------------|
| 2–8 staff, ~5–15 suitable SME clients | Requires **full GL** as primary value |
| Simple bookkeeping, CSV banks | **Payroll** or **tax filing** required |
| English OK, collaborative with founder | Complex **group consolidation**, heavy multi-currency |
| Start small (e.g. **3 clients**), understands scope | **100+ clients** day one, **enterprise SSO**, **24/7 SLA** |
| Accepts founder-assisted onboarding | Expects **zero** founder assistance |

**Recommended ramp:** **ONE firm** → **~3 real SME clients** → expand after first successful close (`FIRST_PAID_PILOT_READINESS_REPORT.md`, `PILOT_MIGRATION_PLAN.md`).

**Target journey:** Firm onboarded → 3 clients → documents → transactions reviewed → bank imported → recon → missing evidence requested → **close readiness** → period closed → reports → feedback.

**Track (no vanity metrics):** time to first client · first useful document workflow · first bank recon · first close · recon completion · # blocked clients + reasons · owner response on requests · support tickets · manual founder interventions · features requested vs ignored · tools they keep using · **would they continue paying?**

---

## SALES — DO NOT CLAIM TODAY

| Never promise | Why (evidence) |
|---------------|----------------|
| Full double-entry accounting / full GL | `PILOT_PRODUCT_SCOPE.md`, schema |
| Payroll, tax filing, VAT engine | Not implemented |
| Automatic Open Banking | CSV only |
| Native Xero/QBO/Sage sync | No integration code |
| Enterprise SSO, 24/7 enterprise SLA | Not implemented |
| Fully autonomous AI bookkeeping | AI optional; **off for Pilot #1** |
| Any feature not verified in current code | Demo ≠ production |

**Safe claims:** evidence-to-close, bank CSV recon, month-end readiness/close, practice portfolio blockers, approved P&L CSV/XLSX, document requests + owner upload (with setup).

---

## ═══════════════════════════════════════════════════════════
## NO REAL CUSTOMER DATA UNTIL THESE ARE GREEN (P0)
## ═══════════════════════════════════════════════════════════

From `FIRST_PAID_PILOT_READINESS_REPORT.md` (8 P0 blockers). **Documentation alone ≠ PASS.**

| # | P0 gate | Status | Evidence / action |
|---|---------|--------|-------------------|
| 1 | **Restore drill passed** (DB + docs) with dated proof | **OPEN** | `deploy/backup/README.md`: recovery **not production-validated** |
| 2 | **Automated DB + document backups** configured + offsite | **OPEN** | Scripts exist; **not automatic until configured** on host |
| 3 | **Production SMTP** (`APP_EMAIL_PROVIDER=smtp`) | **OPEN** | Prod **fails** with log provider — `ProductionEnvironmentValidator` |
| 4 | **TLS + prod secrets + prod compose** on target host | **OPEN** | `docs/VPS_HOSTING.md` — templates ready; **RUNTIME not verified** |
| 5 | **Privacy / pilot terms / DPA baseline** | **EXTERNAL ACTION** | Not in repo — **LEGAL-REVIEW REQUIRED** |
| 6 | **`TenantIsolationIntegrationTest` on Postgres** before real data | **OPEN** | `docs/IMPLEMENTATION_STATUS.md`: **12 tests skipped** without Docker |
| 7 | **AI posture** — pilot firm AI off unless explicit agreement | **OPEN** | Default firm `aiEnabled=true` — ops must disable |
| 8 | **Trial / subscription** — not suspended at day 14 | **OPEN** | Manual ACTIVE / extend-trial (`PILOT_BILLING_PROCESS.md`) |

**Biggest P0 risk:** data loss / **cannot restore** ledger + documents.

---

## GO-LIVE EVIDENCE CHECKLIST (Track B)

Status key: **PASS** = verified run/test · **OPEN** = not done on prod/staging · **EXTERNAL ACTION** · **NOT VERIFIED**

| Done | Item | Status | Reference |
|:----:|------|--------|-----------|
| [ ] | Production-like hosting ready | **OPEN** | `docker-compose.prod.yml`, `docs/VPS_HOSTING.md` |
| [ ] | HTTPS/TLS ready | **OPEN** | VPS guide; proxy required |
| [ ] | Production secrets configured | **OPEN** | `.env` on server; JWT ≠ defaults |
| [ ] | Real SMTP configured | **OPEN** | P0 gap matrix |
| [ ] | Real test email delivered | **NOT VERIFIED** | Reset / document-request path |
| [ ] | Automated DB backup active | **OPEN** | `deploy/backup/run-backup.sh` + timer/cron |
| [ ] | Automated document backup active | **OPEN** | `backup-documents-local.sh` or S3 |
| [ ] | Backup failure detectable | **OPEN** | systemd `OnFailure` unit exists; must deploy |
| [ ] | **DATABASE RESTORE DRILL PASSED** | **OPEN** | `restore-postgres.sh` + evidence |
| [ ] | **DOCUMENT RESTORE DRILL PASSED** | **OPEN** | `restore-documents-local.sh` |
| [ ] | Restored document downloadable | **NOT VERIFIED** | Post-drill check |
| [ ] | Restored financial totals correct | **NOT VERIFIED** | Post-drill spot check |
| [ ] | PostgreSQL tenant-isolation tests passed | **OPEN** | `TenantIsolationIntegrationTest` + Docker |
| [ ] | Authentication verified | **PASS** (code) | `JwtSecurityIntegrationTest` / SECURITY.md |
| [ ] | Authorization verified | **PASS** (code) | Service-layer RBAC |
| [ ] | Owner client-access path proven | **OPEN** | API yes; **Users UI missing** — founder API |
| [ ] | From-scratch registration proven | **PASS** (code/doc) | `FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md` |
| [ ] | First client creation proven | **PASS** (code) | UI CRUD |
| [ ] | Business Owner creation proven | **PASS** (UI) | Password at create |
| [ ] | Business Owner login proven | **OPEN** (ops) | Needs SMTP for reset flows |
| [ ] | Owner upload proven | **PASS** (with API access) | Golden path tests |
| [ ] | Bank import proven | **PASS** (tests) | `GoldenPathWorkflowIntegrationTest` |
| [ ] | Reconciliation proven | **PASS** (tests) | Close blocker when bank exists |
| [ ] | Period close proven | **PASS** (tests) | Readiness engine |
| [ ] | Closed-period protection proven | **PASS** (tests) | `FinancialLifecycleIntegrationTest` |
| [ ] | Reporting proven | **PASS** (code) | Approved-only P&L CSV/XLSX |
| [ ] | Audit trail proven | **PASS** (code) | Firm/client scoped |
| [ ] | Data-exit procedure documented | **PASS** (doc) | `PILOT_DATA_EXIT_PLAN.md` |
| [ ] | Minimum monitoring active | **OPEN** | `/api/v1/health/ready` — P1 |
| [ ] | Minimum alerting active | **OPEN** | Uptime + backup failure — P1 |
| [ ] | Release process rehearsed | **NOT VERIFIED** | `docs/DEPLOYMENT.md` |
| [ ] | Rollback procedure rehearsed | **NOT VERIFIED** | VPS guide |
| [ ] | Privacy/pilot terms reviewed | **EXTERNAL ACTION** | Legal |

**When all P0 green + checklist dry-run complete:** Customer #1 may move to **production data** (`FIRST_PAID_PILOT_GO_LIVE_CHECKLIST.md`).

---

## PARALLEL ENGINEERING / OPS (while selling Track A)

**Sprint 0 (P0)** — do first: host prod/staging, SMTP, secrets, TLS, schedule backups, **restore drill**, run PG integration tests, legal drafts, trial ACTIVE posture.

**P1 during pilot** (activation, not blockers for committed founder-assisted pilot per readiness report): Users UI client-access · category template · post-login next steps · SMTP E2E · monitoring/alerts · release dry run · communicate data exit · ledger coexistence doc per client · bank CSV cheat sheet · owner mobile smoke.

**Do not** start GL, payroll, tax, open banking, Stripe, SSO, SOC2 until Customer #1 validates demand (`PRE_PILOT_DO_NOT_BUILD.md`).

---

## ACCEPTABLE MANUAL PILOT · UNACCEPTABLE SAFETY WORKAROUND

| **ACCEPTABLE manual (Customer #1)** | **UNACCEPTABLE** |
|-------------------------------------|------------------|
| Category setup (no default pack) | **Skipping backups** |
| First ~5 client setups / ~3-client start | **Bypassing tenant security** |
| Initial bank CSV mapping support | **Manual DB edits** to fix customer financial data (except documented break-glass) |
| Manual billing / subscription admin | **Storing real PII without privacy/pilot terms** |
| Weekly onboarding + support calls (`PILOT_SUPPORT_RUNBOOK.md`) | **Real data before restore drill PASS** |
| Data export assistance | **Leaving AI on** without customer agreement |
| **Business Owner client-access via API** if UI not shipped | Promising integrations/features not built |

---

## WEEKLY FOUNDER LOOP

| Day | Focus |
|-----|--------|
| **MONDAY** | Review **Track B** blockers + pilot usage metrics |
| **TUESDAY** | Customer **discovery / demo** calls |
| **WEDNESDAY** | Build/fix **only validated pilot blockers** (P0/P1) |
| **THURSDAY** | Onboarding / support — **observe real workflows** |
| **FRIDAY** | Learning review: What broke? What was manual? Asked vs ignored? **Build vs NOT build?** Update priorities. |

### Feature gate (before significant build)

Did a **real pilot** hit this? How often? What do they do today? Pain of workaround? Impact on activation / retention / pay / trust / efficiency? **Manual for pilot?** Aligns with positioning vs becoming another accounting system?

→ Classify: **BUILD NOW · VALIDATE MORE · MANUAL FOR PILOT · DEFER · DO NOT BUILD**

### De-prioritize unless validation changes it

Full GL · Payroll · Tax engine · Open Banking · Native mobile app · WhatsApp API · Generic workflow builder · Enterprise SSO · SOC2 programme · Multi-country tax · Large BI suite

---

## P1 ACTIVATION REMINDERS (product gaps for self-serve)

- No **default categories** → founder creates template  
- **Empty post-login** → runbook + Zoom  
- **Users UI: no client-access assignment** → `PUT .../client-access` API (`FROM_SCRATCH_A_TO_Z_CLIENT_DEMO.md`)  
- **STARTER user limit (3)** → plan bump for staff + owners  
- **14-day trial** → extend / ACTIVE before go-live  

---

## REFERENCES (deep dive — not daily reading)

| Topic | Doc |
|-------|-----|
| Onboarding steps | `FIRST_CUSTOMER_ONBOARDING_RUNBOOK.md` |
| Ledger / Xero coexistence | `PILOT_LEDGER_COEXISTENCE_STRATEGY.md` |
| Scope & limits | `PILOT_PRODUCT_SCOPE.md` |
| Build order | `FIRST_PAID_PILOT_BUILD_PLAN.md` |
| Gap detail | `FIRST_PAID_PILOT_GAP_MATRIX.md` |
| Demo reset | `FROM_SCRATCH_DEMO_RESET_GUIDE.md` |
| Operator demo | `A_TO_Z_CLIENT_DEMO_OPERATOR_GUIDE.md` |

---

# WHAT IS THE SINGLE MOST IMPORTANT THING I CAN DO TODAY TO GET CUSTOMER #1 SAFELY TO THEIR FIRST SUCCESSFUL MONTH-END CLOSE?

**If Track A has no firm:** Run discovery + demo; qualify coexistence; secure commitment for **3 clients**, not 100.

**If Track B is red:** Execute Sprint 0 — **restore drill + backups + SMTP + TLS + tenant tests + legal** — while using **staging/demo only**.

**If both tracks active:** Founder dry-run empty DB (`FIRST_CUSTOMER_ONBOARDING_RUNBOOK.md`), then onboard firm with **OPTION B or A written down**, categories + owner API access, drive to **first close on one client** and the portfolio **AHA** moment.
