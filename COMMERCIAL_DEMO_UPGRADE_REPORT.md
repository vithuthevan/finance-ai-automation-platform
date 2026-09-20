# Commercial demo upgrade report

**Date:** 2026-09-20  
**Scope:** Customer-acceptance sprint (month-end command center + operational UX)  
**Production deployed:** NO  

---

## Executive summary

The platform now leads with a **Month-end Command Center** backed by real `CloseReadinessService` data, actionable blockers with navigation hints, firm-wide **document request center** with reminders, **admin user → client access UI**, **new-firm onboarding checklist**, and **default category pack** on registration. Demo seed adds portfolio clients for mixed readiness states.

---

## Implemented features

| Priority | Feature | Status |
|----------|---------|--------|
| P1 | Month-end Command Center (`GET /api/v1/work/month-end-command-center`) | YES |
| P1 | Portfolio states READY / ATTENTION / BLOCKED / CLOSED + summary counts | YES |
| P2 | Actionable blockers with `CloseActionLinks` → UI paths | YES |
| P2 | Work queue links + request action URLs improved | YES |
| P2 | Staff workload extended (assigned / ready / blocked / attention per accountant) | YES |
| P3 | Owner portal header emphasizes open request count | YES |
| P4 | Users UI: create with client access + edit access | YES |
| P5 | Onboarding checklist API + dashboard card | YES |
| P5 | Default category pack on firm registration | YES |
| P6 | Document request center + overdue filter + send reminder | YES |
| P6 | Request templates (existing `/api/v1/document-request-templates`) | YES (pre-existing) |
| P7 | Client month-end banner in shell (active client context) | YES |
| P8–P9 | Banking subtitle/guidance (existing); close/period detail unchanged core | PARTIAL |
| P10 | Client activity timeline from audit | DEFERRED |
| P11 | Empty/loading/success patterns extended on new screens | PARTIAL |
| P12 | Demo seed: extra portfolio clients + Ocean draft + ABC bank | YES |

---

## Screens / routes changed

| Route | Change |
|-------|--------|
| `/app/month-end` | **New** — Command Center hero |
| `/app/requests` | **New** — Firm request center |
| `/app/dashboard` | Command center teaser + onboarding checklist |
| `/app/work` | Link to Command Center |
| `/app/users` | Client access on create/edit |
| `/app/owner` | Dynamic “accountant needs N items” header |
| Shell | Nav: Month-end, Requests; client month-end banner |

---

## Backend changes

- `MonthEndCommandCenterService`, `CloseActionLinks`, DTOs for portfolio rows/blockers/progress
- `FirmOnboardingService`, `DefaultCategoryPackService` (called from `FirmService.createFirm`)
- `DocumentRequestCenterService`, `DocumentRequestCenterController`
- `WorkController`: command center + onboarding endpoints
- `PracticeWorkQueueService`: staff portfolio counts via command center; request work URLs
- `StaffWorkloadItemResponse` extended fields

**Schema / migrations:** None (no Flyway changes).

**Security:** Endpoints use existing `@PreAuthorize`, `ClientAccessService` scoping on request center and command center client lists.

---

## Tests executed

| Test | Result |
|------|--------|
| `MonthEndCommandCenterIntegrationTest` | **NOT RUN** — `mvn` not available in agent environment |
| Frontend `npm run build` | **PASS** (after import fixes) |
| Full backend suite | NOT RUN |
| Browser/demo smoke | NOT RUN (requires local stack + seed) |

---

## Demo workflow (intended)

1. Login admin → **Month-end command center** → see Cedar + portfolio clients with mixed states  
2. Open blocked client → blockers with **Reconcile / View requests / Review** actions  
3. Owner upload → accountant **Requests** → complete → readiness updates  
4. Close period → CLOSED state in command center  
5. New firm register → categories seeded → onboarding checklist on dashboard  

---

## Remaining gaps / deferred

| Item | Reason |
|------|--------|
| Client activity timeline | Would need curated audit aggregation — cost vs pilot value |
| Recurring request packs / scheduler | Infrastructure — templates only |
| Full banking/recon UX pass | Partial; recon still CSV-first |
| Automated demo verification in CI | Docker/Maven not run here |

---

## Remaining demo / pilot blockers

- Run `demo/seed_demo.py` after backend up; verify hero BLOCKED → READY → CLOSED live once per environment  
- Starter plan user limit still caps demo users (auditor optional)  
- Xero/QBO coexistence still manual (no integration)  

---

## Files changed (summary)

**Backend:** `module-finance` services/DTOs/close links; `WorkController`; `DocumentRequestCenterController`; `FirmService`; `DocumentRequestJpaRepository`; integration test added  

**Frontend:** `month-end-command-center.page`, `requests.page`, `users.page`, `dashboard.page`, `owner.page`, `work.page`, `shell.component`, `client-month-end-banner.component`, routes, `en.json`  

**Demo:** `demo/seed_demo.py` portfolio helpers  

---

## Recommended next action

**Run the stack locally:** `demo/seed_demo.py` → present **10-minute path** in `COMMERCIAL_DEMO_RUNBOOK.md` starting at Month-end Command Center, ending on Cedar close. Collect one firm interview on whether portfolio view replaces their Excel tracker.
