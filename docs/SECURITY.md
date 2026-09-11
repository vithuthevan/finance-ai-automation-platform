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

- HMAC-signed access tokens; refresh tokens stored server-side (hashed)
- Invalid, expired, malformed, or tampered tokens leave the request unauthenticated (401)
- Deactivated users fail authentication even with a previously issued token
- Production profile: `ProductionJwtSecretValidator` rejects weak/default secrets **and** the well-known `application-local.yml` development JWT value
- Login rate limiting: in-process `AuthRateLimiter` (20 attempts / 60s per key)

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

Access tokens remain Bearer headers (not cookies). Refresh uses an **HttpOnly** `fp_refresh` cookie (`Path=/api/v1/auth`, `SameSite=Lax`, `Secure` in prod). Cookie-authenticated auth POSTs validate `Origin` against `APP_CORS_ALLOWED_ORIGINS` when that list is non-empty. Full Spring CSRF is still disabled for the Bearer API.

## Frontend token storage

Access tokens live **in memory only** (Angular signal). Refresh tokens are **not** stored in `localStorage`; the browser holds them in the httpOnly cookie. On SPA boot, `APP_INITIALIZER` calls `POST /api/v1/auth/refresh` with credentials to restore the session.

The SPA auth interceptor refreshes on HTTP 401 (single in-flight refresh, one retry) and clears the in-memory session if refresh fails.

## Idempotency

Protected financial POSTs **require** an `Idempotency-Key` header (400 `IDEMPOTENCY_KEY_REQUIRED` if missing). The filter fingerprints `method + URI + body hash`. The Angular `ApiService` reuses a stable in-flight key for double-clicks/retries on create/approve/void, bank import/confirm, suggestion accept, and period close.

## Readiness

- `GET /api/v1/health` — process liveness (no dependency checks)
- `GET /api/v1/health/ready` — verifies PostgreSQL connectivity; returns **503** when the database is unreachable

## Secrets

- No real secrets in repository
- `.env.example` uses placeholders only
- Production must provide strong `APP_JWT_SECRET`, database credentials, and storage keys
