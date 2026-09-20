# First paid pilot build plan (minimum)

**Goal:** Safely get Customer #1 to value — not perfection.  
**Effort key:** XS &lt;1d | S 1–2d | M 3–5d | L 1–2w | XL &gt;2w

---

## Bucket A — P0 before real customer data

| # | Item | Effort | Owner | Dependency |
|---|------|--------|-------|------------|
| P0-1 | Provision production host (TLS, secrets, `docker-compose.prod.yml`) | M | DevOps | Domain, VPS |
| P0-2 | Strong `APP_JWT_SECRET`, prod profile, CORS, cookie secure | XS | DevOps | P0-1 |
| P0-3 | PostgreSQL on volume/managed; **automated** `run-backup.sh` + offsite | S | DevOps | P0-1 |
| P0-4 | Document backup (volume tar or S3) aligned with DB backup | S | DevOps | P0-3 |
| P0-5 | **Restore drill** with evidence (DB + docs + smoke test) | M | DevOps | P0-3, P0-4 |
| P0-6 | Production SMTP + `APP_FRONTEND_BASE_URL`; test reset + request email | S | DevOps | P0-1 |
| P0-7 | Pilot legal pack (privacy, terms, pilot DPA draft) | M | Founder/Legal | LEGAL-REVIEW |
| P0-8 | Run Postgres integration suite in CI/staging (Docker) before go-live | S | Eng | CI runner Docker |

**P0 engineering in repo:** Minimal (0–2 days) if ops-heavy; product code may be unchanged.

---

## Bucket B — P1 activation (credible pilot UX)

| # | Item | Effort | Owner | Dependency |
|---|------|--------|-------|------------|
| P1-1 | Users UI: assign/replace client access on create/edit | M | Eng | — |
| P1-2 | Default bookkeeping category template on firm create (optional toggle) | S | Eng | Flyway seed function |
| P1-3 | Post-register “next steps” panel or checklist (static links) | S | Eng | — |
| P1-4 | Firm settings: default `aiEnabled=false` for new firms | XS | Eng | Product decision |
| P1-5 | Platform admin playbook tested (extend trial, ACTIVE, plan bump) | XS | Ops | P0-1 |
| P1-6 | Health + backup failure alerting | S | DevOps | P0-3 |

---

## Bucket B — P1 workflow

| # | Item | Effort | Owner | Dependency |
|---|------|--------|-------|------------|
| P1-7 | Bank CSV mapping doc for top 2 LK banks | XS | CS | — |
| P1-8 | Owner portal mobile QA fixes (if any found) | S | Eng | P1-1 |

---

## Bucket B — P1 operations

| # | Item | Effort | Owner | Dependency |
|---|------|--------|-------|------------|
| P1-9 | Release checklist executed once on staging | S | Eng | P0-1 |
| P1-10 | Founder API script for bulk client-access (optional) | XS | Eng | — |

---

## MANUAL FOR PILOT

- Category naming alignment with Xero
- Migration of 5 clients
- Billing/invoicing
- Weekly calls
- Client-access API until P1-1 ships
- AI off + pilot agreement for AI later

---

## VALIDATE FIRST

- Xero coexistence willingness (OPTION B)
- Pilot pricing
- PDF reports need
- WhatsApp reminders
- Recurring request packs

---

## LATER (P2+)

- Client CSV import
- Stripe
- Xero read-only
- PDF reports
- MFA
- S3 mandatory abstraction UI

---

## Proposed sprint sequence

### Sprint 0 — P0 safety (ops-heavy)

**Objective:** Production environment with backup, restore proof, SMTP, TLS.

- Deliverables: P0-1–P0-8, go-live checklist items for infra
- **No feature scope** unless blocked

### Sprint 1 — P1 activation

**Objective:** Owner and staff onboarding without API workarounds.

- P1-1, P1-2, P1-3, P1-4

### Sprint 2 — P1 operations polish

**Objective:** Supportability and release confidence.

- P1-5, P1-6, P1-9, documentation handoff

**Estimated sprints:** 3  
**Minimum calendar time:** 2–4 weeks (parallel ops + 1 eng sprint)

---

## Dependencies graph

```
P0-1 Hosting → P0-3 Backup → P0-5 Restore drill
P0-1 → P0-6 SMTP
P1-1 Client access UI → owner onboarding (reduces founder API)
```
