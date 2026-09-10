# Security Model — Finance Platform V1

This document describes the security architecture implemented for V1. It is not a formal certification.

## Tenant isolation

- Every firm-scoped row is tied to `firm_id`.
- JWTs carry `firmId`, but **authorization always re-loads the user from the database** and verifies `user.firmId` matches the token claim (`JwtAuthenticationFilter`).
- Cross-firm ID access returns **404** (resource not found) or **403** according to endpoint conventions.
- Integration coverage: `TenantIsolationIntegrationTest`, `ClientCreationIntegrationTest`, `CategoryCreationIntegrationTest`.

## Client assignment

- Accountants are limited to clients in `user_client_access`.
- Admins retain firm-wide access.
- Auditors never see draft ledger rows.
- `UPLOAD_ONLY` business owners may upload/respond to requests but cannot access reports, banking, or internal work queues.

## Roles

| Role | Ledger write | Approve/void | Close | Reports | Banking |
|------|--------------|--------------|-------|---------|---------|
| ADMIN | Yes (firm) | Yes | Yes | Yes | Yes |
| ACCOUNTANT | Assigned clients | Assigned | Assigned | Assigned | Assigned |
| AUDITOR | No | No | No | Read finalized | No |
| BUSINESS_OWNER | No | No | No | Own client | No |
| UPLOAD_ONLY owner | No | No | No | No | No |

## Platform administration

**V1 change (Phase 9):** platform admin is no longer authorized via a runtime email allowlist.

- Table: `platform_admin_grants` (migration `V24__platform_admin_grants.sql`)
- Runtime check: `PlatformAdminService.isPlatformAdmin(userId)` using persisted `active` grants
- Bootstrap only: `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` creates the **first** grant at startup via `PlatformAdminBootstrapRunner`
- Grant/revoke APIs: `POST /api/v1/platform/admins/{userId}/grant|revoke` (audited)
- Firm admins **cannot** access `/api/v1/platform/*`
- Platform APIs expose firm/subscription metadata only — not client ledgers, documents, or bank data

## JWT security

- HMAC-signed access tokens; refresh tokens stored server-side
- Invalid, expired, malformed, or tampered tokens leave the request unauthenticated (401)
- Deactivated users fail authentication even with a previously issued token
- Production profile: `ProductionJwtSecretValidator` rejects weak/default `JWT_SECRET`
- Login/password-reset rate limiting: in-process `AuthRateLimiter` (20 attempts / 60s per key)

## File upload

- MIME sniffing + extension checks
- Size limits enforced at Spring multipart and application layer
- Storage keys are server-generated; original filenames never control paths
- Tests: `FileUploadSecurityIntegrationTest`

## AI trust boundary

- AI suggestions create **DRAFT** transactions only; never auto-approve
- Mock/disabled providers used in tests (`app.ai.enabled=false`)
- Invalid provider output must pass domain validation (category, amount, firm)
- OpenAI provider bean is conditional on `app.ai.openai.api-key`

## Closed periods

- Writes (approve, void, reconcile, bank confirm) blocked when period status is `CLOSED`
- Covered by close workflow services and period guards (extend with dedicated tests as needed)

## Subscription read-only behavior

- `SUSPENDED` firms: reads/exports per policy; writes blocked
- Quota enforcement uses pessimistic locking on subscription rows for client/user limits

## Production headers

Configure at reverse proxy (Nginx/cloud):

- `Strict-Transport-Security` (HTTPS only)
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy`
- `Permissions-Policy`
- Sensible `Content-Security-Policy` for the Angular SPA (avoid breaking Material/fonts)

## CSRF

JWT is sent via `Authorization: Bearer` header, not cookies, for API calls. CSRF protection is disabled for the stateless API. If cookie-based refresh is introduced later, CSRF must be revisited.

## Frontend token storage

V1 stores the access token in `localStorage` (`fp.session`). This is acceptable for V1 with short-lived access tokens and strong XSS hygiene; migrating refresh tokens to `HttpOnly` cookies is a Phase 10+ hardening item.

The SPA auth interceptor refreshes on HTTP 401 (single in-flight refresh, one retry) and clears the session if refresh fails.

## Idempotency

Mutating financial POSTs accept an `Idempotency-Key` header. The filter runs on the security chain after JWT authentication. The Angular `ApiService` sends a UUID key for create/approve/void expense & income, bank import/confirm, suggestion accept, and period close.

## Readiness

- `GET /api/v1/health` — process liveness (no dependency checks)
- `GET /api/v1/health/ready` — verifies PostgreSQL connectivity; returns **503** when the database is unreachable

## Secrets

- No real secrets in repository
- `.env.example` uses placeholders only
- Production must provide strong `JWT_SECRET`, database credentials, and storage keys
