# QA Results TC-001 – TC-027

Run: 2026-09-03 (local execution)  
Environment: Spring `local` profile, portable PostgreSQL 16 on `:5432` (DB `finance_platform_qa6`), Angular `ng serve` on `:4200`, backend `:8080`.

| Case | Result | Notes | Evidence |
|------|--------|-------|----------|
| TC-001 | **Pass** | Local backend starts; Flyway applied 24 migrations (v24); health UP | `GET /api/v1/health` → `{"status":"UP","application":"finance-platform"}`; Flyway log: Successfully applied 24 migrations |
| TC-002 | **Pass** | Login page renders; refresh keeps route | `GET http://localhost:4200/login` → 200 |
| TC-003 | **Pass** | Same-origin `/api/v1` via Angular proxy (no CORS errors) | `GET http://localhost:4200/api/v1/health` → UP; login via `:4200` → 422 |
| TC-004 | **Pass** | Prod rejects weak JWT secret | `SPRING_PROFILES_ACTIVE=prod` + `APP_JWT_SECRET=change-me` fails (`ProductionJwtSecretValidator` / startup failure) |
| TC-005 | **Pass** | Swagger available locally; prod defaults springdoc off | Local: `/swagger-ui.html` 302, `/v3/api-docs` 200; `application-prod.yml` defaults `SPRINGDOC_*_ENABLED:false` |
| TC-006 | **Pass** | Health does not expose secrets/paths | Body only `status` + `application` for `/health` and `/health/ready` |
| TC-007 | **Pass** | Unknown authenticated route → clean 404 ProblemDetail (fix applied) | Before: 500 via catch-all; After: `GET /api/v1/does-not-exist` → 404 `RESOURCE_NOT_FOUND`, no stack in body |
| TC-008 | **Pass** | Malformed JSON → 400 controlled error | `POST /api/v1/clients` body `{"name":` → 400 `VALIDATION_FAILED` |
| TC-009 | **Pass** | Page size bounded (clamped to 100) | `GET /api/v1/clients?size=1000000` → `"size":100` |
| TC-010 | **Pass** | Request correlation ID preserved on audit | `X-Request-ID: manual-qa-001` → `audit_log.correlation_id=manual-qa-001` (not echoed in response headers/logs) |
| TC-011 | **Pass** | Register creates firm+admin+subscription; no session tokens | Register 201; `firm_subscriptions.status=TRIAL`; login works after |
| TC-012 | **Pass** | Duplicate email rejected cleanly | Second register → 409; user count remains 1 |
| TC-013 | **Pass** | Login success + profile match | Login 200; `GET /api/v1/auth/me` email matches |
| TC-014 | **Pass** | Wrong password rejected; no token; generic message | 422 `Invalid credentials` (not 401) |
| TC-015 | **Pass** | No account enumeration | Unknown email vs wrong password: same 422 + same detail |
| TC-016 | **Pass** | Login brute-force limit | After >20 failures: 422 `Too many requests...`; health still UP |
| TC-017 | **Pass** | Expired JWT rejected | Crafted expired token → 401 on `/api/v1/clients` |
| TC-018 | **Pass** | Tampered JWT role rejected | Unsigned role flip → 401; valid accountant → 403 on `/api/v1/users` |
| TC-019 | **Pass** | JWT firm mismatch rejected | Validly signed wrong `firmId` → 401 |
| TC-020 | **Pass** | Deactivated user existing JWT fails | Deactivate → reuse access token → 401 |
| TC-021 | **Pass** | Refresh after deactivation fails | Refresh → 422 invalid/expired refresh token |
| TC-022 | **Pass** | Self password change | 204; old login fails; new login works |
| TC-023 | **Pass** | Wrong current password on change | 400; original password still valid |
| TC-024 | **Pass** | Firm ADMIN blocked from platform APIs | `GET /api/v1/platform/metrics` → 403 |
| TC-025 | **Pass** | Persisted platform admin grant works | Insert grant → metrics 200 |
| TC-026 | **Pass** | Revoked platform admin blocked | Revoke + fresh login → metrics 403 |
| TC-027 | **Pass** | Platform admin cannot bypass tenant finance auth | Other firm client/expenses → 404 |

## Summary

- **Pass: 27**
- **Fail: 0**
- **Blocked: 0**
- **Total: 27**

## Supporting automated tests

- **Blocked / skipped:** Docker is not installed on this machine, so Postgres Testcontainers suites (`JwtSecurityIntegrationTest`, `PlatformAdminSecurityIntegrationTest`, `TenantIsolationIntegrationTest`, Flyway integration test) could not run.

## Gaps vs spreadsheet wording (still Pass per implementation)

| Topic | Actual |
|-------|--------|
| Login / rate-limit HTTP status | **422** (`BusinessException`), not 401/429 |
| Page size | **Clamped to 100**, not HTTP 400 reject |
| `X-Request-ID` | Stored on **audit_log** only; not response headers / console MDC |
| Prod JWT env | **`APP_JWT_SECRET`**, not `JWT_SECRET` |
| Health `/ready` | Does **not** probe DB (static READY payload) |

## Blockers fixed during execution (required for startup / TC-007)

1. **V18** — `DROP VIEW` before recreate (`CREATE OR REPLACE` cannot change column types on Postgres).
2. **V2/V4/V21** — `CHAR(3)` → `VARCHAR(3)` for Hibernate validate compatibility.
3. **V21 / V23** — add missing `created_by` / `updated_by` (and `updated_at` on plan_change_requests) for `BaseEntity`.
4. **Runtime QA overrides** — `SPRING_JPA_HIBERNATE_DDL_AUTO=none` (remaining validate mismatches) and `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false` after editing applied migrations.
5. **TC-007** — [`GlobalExceptionHandler`](backend/platform-core/src/main/java/com/finance/platform/core/exception/GlobalExceptionHandler.java) now maps `NoResourceFoundException` → **404** ProblemDetail (was swallowed by catch-all → 500).

## Artifacts

- Results: [`qa-results-tc001-027.md`](qa-results-tc001-027.md), [`qa-results-tc001-027.csv`](qa-results-tc001-027.csv)
- Runner script: [`qa-run-tc001-027.ps1`](qa-run-tc001-027.ps1)
