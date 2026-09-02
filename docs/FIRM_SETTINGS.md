# Firm Settings

Firm settings are **accounting and workspace configuration** for an accounting firm. They are separate from SaaS subscription/billing configuration.

## Settings

Stored on the `firms` table (extended in `V15__firm_settings_and_subscriptions.sql`):

| Setting | Default | Notes |
|---------|---------|-------|
| Name | — | Display / business name |
| Currency | `LKR` | ISO 4217 code; validated on update |
| Timezone | `Asia/Colombo` | IANA zone name; validated with `ZoneId.of` |
| Financial year start month | `4` (April) | Integer 1–12; groundwork for future fiscal reporting |
| AI enabled | `true` | Firm-level switch for automatic AI extraction |

## API

- `GET /api/v1/firm` and `GET /api/v1/settings/firm` (alias)
- `PUT /api/v1/firm` and `PUT /api/v1/settings/firm` (alias)

Authorization:

- **Read:** `ADMIN`, `ACCOUNTANT`, `AUDITOR`
- **Write:** `ADMIN` only

Audit action: `FIRM_SETTINGS_UPDATED` (before/after snapshot).

## Validation

- **Currency:** must be a known ISO 4217 code → `INVALID_CURRENCY`
- **Timezone:** must be a valid IANA zone → `INVALID_TIMEZONE`
- **Financial year month:** 1–12

## Effects on existing data

### Currency

Changing default currency does **not** rewrite historical transactions. Existing amounts keep their stored currency/values.

### Timezone

Changing timezone does **not** rewrite stored timestamps. It affects how the firm configures future date-sensitive behaviour.

### Financial year

Phase 5 month-end close continues to use **calendar months**. The financial year start month is stored for future annual/fiscal templates only.

### AI enabled

When `aiEnabled = false`:

- Document upload works
- Manual document review works
- Manual transaction creation works
- Reporting and reconciliation work
- No external AI processing is started

This is independent of AI **quota** (subscription limit). Quota only applies when AI is enabled and the platform AI provider is configured.

## Frontend

**Administration → Firm settings** (`/app/firm`):

- General — name, currency, timezone
- Accounting — financial year start
- Automation — AI assistance toggle
- Subscription — link to `/app/subscription`

Firm ADMIN also has **Subscription** in the sidebar for plan usage and upgrade requests.

## What firm admins cannot configure

- External AI provider credentials (platform configuration)
- Subscription plan or status
- Usage counters
- Payment methods

Those are platform-operator concerns or future billing integration.

## Privacy by role

- **BUSINESS_OWNER** does not see subscription pricing or limits
- **AUDITOR** does not manage firm settings or subscription
