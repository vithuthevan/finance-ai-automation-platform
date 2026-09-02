# SaaS Subscriptions

Phase 8 introduces commercially controllable multi-tenant subscriptions without payment processing.

## Plans

Plans are persisted in `subscription_plans` and seeded by Flyway `V23__saas_plans_phase8.sql`.

| Code | Clients | Users | Documents/month | AI/month | Storage |
|------|---------|-------|-----------------|----------|---------|
| STARTER | 10 | 3 | 500 | 100 | 2 GB |
| PRACTICE | 50 | 10 | 3,000 | 1,000 | 10 GB |
| PROFESSIONAL | 200 | 30 | 15,000 | 5,000 | 50 GB |

Limits are copied onto each `firm_subscriptions` row when a plan is applied so historical subscription rows remain meaningful if catalog values change later.

Stable plan **codes** (`STARTER`, `PRACTICE`, `PROFESSIONAL`) are used everywhere; display names are not used in business logic.

## Subscription lifecycle

Each firm has one row in `firm_subscriptions`.

Statuses:

- **TRIAL** — default for new firms (14 days by default, configurable)
- **ACTIVE** — paid or manually activated
- **PAST_DUE** — reserved for future billing integration; writes still allowed in V1
- **SUSPENDED** — read-only for operational writes
- **CANCELLED** — read-only for operational writes

### Registration

`FirmService.createFirm()` calls `SubscriptionService.createDefaultForFirm()` atomically in the same transaction:

- Plan from `app.subscription.default-plan` (default `STARTER`)
- Status `TRIAL`
- Trial end from `app.subscription.default-trial-days` (default `14`)
- Billing period start/end set to the current calendar month

### Trial expiry

`SubscriptionMaintenanceScheduler` runs daily (`app.subscription.maintenance-cron`):

- Notifies firm ADMIN at 7, 3, and 1 days before trial end
- On expiry: status → `SUSPENDED`, notification sent
- **No accounting data is deleted**

### Suspension / cancellation policy

`SubscriptionAccessService.assertCanWrite()` blocks:

- New clients/users
- Document uploads
- Bank imports
- Other paths that call the guard

Read endpoints (reports, exports, historical documents, audit) remain available according to existing RBAC.

## Usage metering

`UsageService` + `UsageQueryRepository` derive usage from source tables:

| Metric | Source |
|--------|--------|
| Active clients | `clients` where `active = true` and not deleted |
| Active users | `users` where `active = true` and not deleted |
| Documents this period | `receipts.uploaded_at` within subscription period |
| AI this period | `document_processing_attempts` with `SUCCESS` or `FAILED` in period |
| Storage | `SUM(receipts.file_size_bytes)` for non-deleted receipts |

Monthly document/AI usage follows `current_period_start` / `current_period_end` on the subscription.

## Quota enforcement

Enforced server-side via `SubscriptionAccessService` (`SubscriptionQuotaGuard`):

| Limit | Where enforced | Error code |
|-------|----------------|------------|
| Clients | `ClientService` create/reactivate | `PLAN_CLIENT_LIMIT_REACHED` |
| Users | `UserService` create/reactivate | `PLAN_USER_LIMIT_REACHED` |
| Documents | `DocumentService.upload` (before storage) | `PLAN_DOCUMENT_LIMIT_REACHED` |
| Storage | `DocumentService.upload` (before storage) | `PLAN_STORAGE_LIMIT_REACHED` |
| AI processing | `DocumentAiProcessor` (skip, not fail upload) | `PLAN_AI_LIMIT_REACHED` |
| Write access | Bank import and other guarded writes | `SUBSCRIPTION_SUSPENDED` / `SUBSCRIPTION_INACTIVE` |

### AI quota behaviour

When AI quota is exhausted:

- Document upload succeeds
- AI extraction is skipped with a clear message
- Manual review and bookkeeping continue normally

### Over-limit after downgrade

If a plan is downgraded below current usage (e.g. 32 active clients on a 10-client plan):

- Existing records are preserved
- New creates/reactivations are blocked until usage falls below the limit
- `SubscriptionUsageView.overLimit` is `true`

### Business owner privacy

`BUSINESS_OWNER` upload failures for firm quota map to generic `UPLOADS_UNAVAILABLE` without exposing commercial plan details.

## APIs

Firm ADMIN:

- `GET /api/v1/subscription` — summary
- `GET /api/v1/subscription/usage` — limits and usage
- `POST /api/v1/subscription/upgrade-request` — manual upgrade request

Platform operator (email allowlist, not firm ADMIN role):

- `GET /api/v1/platform/me`
- `GET /api/v1/platform/metrics`
- `GET /api/v1/platform/firms`
- `GET /api/v1/platform/firms/{id}`
- `PUT /api/v1/platform/firms/{id}/subscription/plan`
- `PUT /api/v1/platform/firms/{id}/subscription/status`
- `POST /api/v1/platform/firms/{id}/subscription/extend-trial`

Platform APIs return firm metadata and resource usage only — not client financial records.

## Notifications

Phase 7 notification infrastructure is used for:

- Usage thresholds at 80%+ (documents, AI, storage) — deduplicated per metric/percent
- Trial ending (7/3/1 days, expired)
- Subscription suspended

Recipients: firm **ADMIN** only.

## Manual billing

`BillingProvider` / `ManualBillingProvider` is a no-op abstraction for future payment integration. V1 has no card capture, invoices, or automatic charging.

## Configuration

```properties
app.subscription.default-plan=STARTER
app.subscription.default-trial-days=14
app.subscription.maintenance-cron=0 30 7 * * *
app.platform.admin-emails=ops@example.com
```

Platform admin is determined by authenticated user email matching `app.platform.admin-emails` (comma-separated). This is intentionally separate from the firm `ADMIN` role.

## Audit events

- `SUBSCRIPTION_CREATED`
- `SUBSCRIPTION_PLAN_CHANGED`
- `SUBSCRIPTION_ACTIVATED`
- `SUBSCRIPTION_SUSPENDED`
- `TRIAL_EXTENDED`
- `PLAN_CHANGE_REQUESTED`

## Core product rule

**Subscription controls access to service; subscription never destroys accounting history.**
