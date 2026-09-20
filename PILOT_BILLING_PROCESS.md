# Pilot billing process — manual (no Stripe)

**CODE-VERIFIED:** No payment provider; `ManualBillingProvider`; subscription enforced in app (`docs/SAAS_SUBSCRIPTIONS.md`).

---

## Pilot #1 billing model (hypothesis — CUSTOMER-VALIDATION REQUIRED)

| Component | Suggested hypothesis | Notes |
|-----------|---------------------|--------|
| 90-day pilot fee | Fixed fee (amount TBD with customer) | Covers founder onboarding + weekly calls |
| Monthly thereafter | Per-firm monthly (TBD) | If pilot converts |
| Implementation fee | Optional one-time | Category setup, migration, training |
| Per-client metering | Defer | STARTER plan already limits clients (10) |

**Do not claim market WTP without signed pilot.**

---

## How Pilot #1 is billed manually

1. **Contract:** Pilot agreement (scope, data, exit, support hours) — LEGAL-REVIEW REQUIRED
2. **Invoice:** Founder sends PDF invoice (bank transfer / Wise / local payment)
3. **Platform state:** Ops sets firm subscription:
   - `PUT /api/v1/platform/firms/{id}/subscription/status` → `ACTIVE`
   - `POST .../subscription/extend-trial` if staying on TRIAL with extended date
   - `PUT .../subscription/plan` → PRACTICE if >3 users or >10 clients needed
4. **No card capture** in application

---

## Subscription behavior relevant to billing

| Status | Writes | Reads/exports |
|--------|--------|---------------|
| TRIAL | Yes until trial end | Yes |
| TRIAL expired → SUSPENDED | Blocked (`SUBSCRIPTION_SUSPENDED`) | Yes (policy) |
| ACTIVE | Yes within quotas | Yes |
| CANCELLED | Read-only | Per policy |

**P1 ops:** Before Customer #1 go-live, platform admin must **extend trial ≥ pilot length OR set ACTIVE** to avoid day-14 surprise lockout.

---

## Quotas (STARTER default)

| Limit | Value |
|-------|-------|
| Clients | 10 |
| Users | 3 |
| Documents/month | 500 |

Pilot firm with 5 clients + admin + 2 staff + 5 owners = **may exceed 3 users** → upgrade plan or ops adjustment **before** onboarding owners as full users. Consider UPLOAD_ONLY owners or shared demo pattern — **validate with customer**.

---

## Audit trail

- `SUBSCRIPTION_ACTIVATED`, `TRIAL_EXTENDED`, `SUBSCRIPTION_PLAN_CHANGED` in audit log

---

## What NOT to do

- Do not implement Stripe in pilot sprint
- Do not block pilot on automated dunning
- Do not delete data on non-payment without written policy
