# Pilot support runbook — Customer #1

**Support model:** Named founder contact; business-hours response; weekly onboarding + feedback; emergency channel (phone/WhatsApp) by agreement — **not** 24/7 enterprise SLA.

---

## Channels

| Channel | Use |
|---------|-----|
| Primary email | support@… (founder) |
| Emergency | Agreed mobile — data incidents only |
| Weekly Zoom | Onboarding + backlog review |
| In-app | User reports issue + screenshot |

---

## Severity definitions

| Level | Example | Response target (pilot) |
|-------|---------|-------------------------|
| S1 Data incident | Suspected cross-firm data, data loss | Immediate; freeze deploy |
| S2 Workflow blocked | Cannot close month, login down | Same business day |
| S3 Degraded | Email not delivered, slow report | 1–2 business days |
| S4 How-to | Category setup, CSV mapping | Next call or email |

---

## Playbooks

### “Bank import failed”

1. Get client id, bank account id, CSV sample (redact if needed)
2. Check error code in UI / API response
3. Verify `SUBSCRIPTION_SUSPENDED` not active
4. Check file size < 15MB; encoding UTF-8
5. Review mapping profile columns
6. Logs: correlation id from response header if enabled
7. Re-import: duplicate prevention may require new file or undo import path

**Tools:** Admin JWT; no DB required for most cases.

### “I can’t login”

1. Email global uniqueness — wrong firm?
2. `emailVerificationRequired` — if true, verify email sent (SMTP)
3. Rate limit (20/min) — wait or ops adjust proxy
4. User `active=false` — admin reactivate
5. Password reset — requires **SMTP** in prod

### “Client can’t upload”

1. Owner has client access? (**#1 issue** — check `user_client_access`)
2. Role UPLOAD_ONLY vs BUSINESS_OWNER
3. Subscription write guard / quota
4. File type MIME allowlist
5. Closed period rules (unlikely for upload)

### “My P&L looks wrong”

1. Confirm **APPROVED** only in reports
2. Date range vs transaction_date
3. VOID excluded
4. Drafts not in P&L — expected
5. Compare to bank recon matched lines only if they expect cash basis

### “Month won’t close”

1. `GET .../periods/{id}/readiness` — list blockers
2. Common: open document requests (UPLOADED counts), drafts, unlinked docs, **bank recon incomplete**
3. Warnings do not block at 100%

### “Document disappeared”

1. Not deleted if linked to APPROVED — check audit `DOCUMENT_DELETED`
2. Storage object missing → `DOCUMENT_OBJECT_MISSING` — **DR incident** if backup gap
3. Wrong client filter in UI

---

## Admin capabilities (CODE-VERIFIED)

| Action | Who |
|--------|-----|
| Extend trial / change plan / suspend firm | Platform admin API |
| Grant platform admin | Bootstrap + grant API |
| Reset user password | User self-service reset email OR admin creates new password via user update |
| Client access fix | `PUT /users/{id}/client-access` |
| View firm subscription usage | Firm ADMIN + platform |

**Avoid:** Raw production SQL except disaster recovery.

---

## Escalation — data incident

1. Preserve logs and backup snapshot time
2. Identify scope (firm ids)
3. Notify customer within agreed window
4. Run restore drill playbook if data loss (`deploy/backup/`)
5. Post-incident: tenant isolation test re-run

---

## Monitoring minimum (pilot)

| Signal | How |
|--------|-----|
| API down | `GET /api/v1/health/ready` external ping (5 min) |
| Disk full | VPS `df` or provider alert |
| Backup job failed | systemd `OnFailure` or cron email |
| 5xx spike | Nginx/access log review daily (manual OK) |

**P1:** Configure at least health + backup failure alerts before real data.

---

## What requires developer DB access today

- Firm-wide bulk export not in UI
- Orphan storage reconciliation
- Trial/plan mistakes if platform admin not configured
- Corrupted idempotency keys (rare)

**Goal:** Reduce to zero for routine pilot support via platform admin + API.
