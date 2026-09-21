# Month-end operating platform upgrade



**Date:** 2026-09-20  

**Production deployed:** NO  



## Summary



The product is refocused on **accounting-firm month-end operations**: portfolio readiness (Command Center), **waiting-on-client vs your-team** accountability, configurable **monthly evidence checklists** with one-click request generation, request center/reminders, owner upload home, work queue, user–client access UI, onboarding checklist, and demo data for mixed portfolio states — without Postman/SQL during the live demo.



## Implemented capabilities



| Area | Detail |

|------|--------|

| Month-End Command Center | `GET /api/v1/work/month-end-command-center` — READY / ATTENTION / BLOCKED / CLOSED from `CloseReadinessService` |

| Responsibility | Blockers carry `responsibility` (`CLIENT` \| `TEAM`) and labels; `focus` filter: `WAITING_ON_CLIENT`, `TEAM_ACTION` |

| Period selection | `year`, `month` query params + UI on `/app/month-end` |

| Client month-end summary | Shell banner + close detail — blockers grouped by responsibility with action links |

| Monthly evidence | `client_monthly_evidence_items` + CRUD + `POST .../generate-requests` |

| Request center | `/app/requests` — firm-wide list, overdue filter, send reminder |

| Request templates | `GET /api/v1/document-request-templates` |

| Owner home | `/app/owner` — outstanding uploads, received/completed |

| Work queue | `/app/work` — actionable items with responsibility hints |

| Staff workload | `GET /api/v1/work/staff-workload` (admin) |

| User → client access | `/app/users` — create user, roles, client assignments |

| Onboarding | `GET /api/v1/work/onboarding-checklist` — includes configure evidence step |

| Dashboard | Prioritizes command center summary and operational cards |

| Demo seed | 8 clients (Cedar + 7 portfolio), Cedar checklist, bank CSV, open rent request, draft expense on Ocean Traders |



## Schema changes (Flyway)



- **V30__monthly_evidence_checklist.sql** — `client_monthly_evidence_items`



## API changes



| Endpoint | Method | Notes |

|----------|--------|-------|

| `/api/v1/work/month-end-command-center` | GET | `state`, `focus`, `year`, `month`, `query`, `accountantUserId`, `clientId` |

| `/api/v1/clients/{clientId}/monthly-evidence` | GET, POST | Checklist items |

| `/api/v1/clients/{clientId}/monthly-evidence/{itemId}` | PUT | Update item |

| `/api/v1/clients/{clientId}/monthly-evidence/generate-requests` | POST | Creates document requests (no auto-email) |

| `/api/v1/practice/document-requests` | GET | Request center pagination |

| `CloseFindingResponse` | — | `responsibility`, `responsibilityLabel` |



## Frontend changes



- `/app/month-end` — period + status + focus filters; blockers by responsibility

- `/app/clients/monthly-evidence?clientId=` — checklist + generate requests

- Client shell banner — period, status, waiting-on-client / your-team blockers, quick actions

- Close period detail — responsibility on blockers

- Dashboard, work queue, requests, owner home, users admin (existing routes extended)



## Security



- New endpoints use existing `@PreAuthorize` and `ClientAccessService` scoping.

- `app.auth.rate-limit-enabled` (default `true`); disabled in test profiles only.

- No auth weakening for production.



## Tests (executed this session)



| Suite | Result |

|-------|--------|

| Backend `compileJava` | **PASS** |

| `MonthEndCommandCenterIntegrationTest` (3) | **PASS** |

| `ClientMonthlyEvidenceIntegrationTest` (1) | **PASS** |

| `TenantIsolationIntegrationTest` (9) | **PASS** |

| `module-finance:test` | **PASS** |

| Full `:platform-app:test` (70) | **PARTIAL** — some H2 profile tests return 400 on expense create; `GoldenPathWorkflowIntegrationTest` flaky on async document extraction timing |

| Frontend `npm run build` | **PASS** |

| Frontend `npm run test` (vitest) | **PASS** (2 tests) |

| Browser / Playwright E2E | **NOT RUN** (requires `docker compose` + `demo/seed_demo.py`) |



### Test infrastructure fixes



- **`PostgresTestContainer`** — shared Testcontainers Postgres across integration test classes (fixes connection refused between classes).

- **`app.auth.rate-limit-enabled: false`** in `application-integrationtest.yml` / `application-test.yml` — prevents 429 during dense register/login in tests.



## Deferred



| Item | Reason |

|------|--------|

| Automatic recurring request scheduler | One-click generation is safer for pilot |

| Client activity timeline | Curated audit aggregation — cost vs value |

| Full banking UX pass | CSV reconciliation remains wedge |

| Xero/QBO/Sage | Out of phase scope |



## Demo



1. Start backend + frontend; `python demo/seed_demo.py`

2. Admin → **Month-end command center** — filter **Waiting on client** → Cedar Café

3. Clients → Cedar → **Evidence** → optional **Generate requests**

4. Owner login → upload rent invoice → accountant **Requests** → complete / review

5. Banking → reconcile → Close → **BLOCKED → READY → CLOSED**



See `COMMERCIAL_DEMO_RUNBOOK.md` for the full narrative.



## Main modules touched



- `module-finance`: `MonthEndCommandCenterService`, `CloseReadinessService`, `ClientMonthlyEvidenceService`, `CloseResponsibility*`, `PracticeWorkQueueService`, `DocumentRequestCenterService`, DTOs, entity, repository

- `module-auth`: `AuthRateLimiter` + `AuthProperties.rateLimitEnabled`

- `platform-app`: controllers, V30 migration, `PostgresTestContainer`, integration tests

- `frontend`: month-end, monthly-evidence, client banner, close, dashboard, requests, owner, users

- `demo/seed_demo.py`


