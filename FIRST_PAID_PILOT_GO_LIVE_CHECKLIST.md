# First paid pilot go-live checklist

Binary checks — complete before Customer #1 production data.

## Security & tenancy

- [ ] `TenantIsolationIntegrationTest` passed on staging (Docker Postgres) — **TEST-VERIFIED**
- [ ] `JwtSecurityIntegrationTest` passed on staging
- [ ] `FileUploadSecurityIntegrationTest` passed on staging
- [ ] Production JWT secret ≠ local/docker defaults — **CODE-VERIFIED** `ProductionJwtSecretValidator`
- [ ] `SPRING_PROFILES_ACTIVE=prod` on production backend
- [ ] CORS limited to real frontend origin(s)
- [ ] TLS enabled (HTTPS); HSTS at proxy
- [ ] Platform admin via grants (not demo bootstrap email left enabled)

## Data durability

- [ ] PostgreSQL on persistent volume or managed service
- [ ] Automated DB backup schedule configured — **EXTERNAL-INFRASTRUCTURE**
- [ ] Document storage durable (volume + backup or S3) — **EXTERNAL-INFRASTRUCTURE**
- [ ] Offsite backup copy enabled
- [ ] **Restore drill passed** with dated evidence — **RUNTIME-VERIFIED** (required)
- [ ] RPO/RTO agreed with customer (e.g. RPO 24h, RTO 4h pilot)

## Application config

- [ ] `APP_FRONTEND_BASE_URL` correct for email links
- [ ] `APP_EMAIL_PROVIDER=smtp` with working MAIL_* — **prod fails on log provider**
- [ ] Test email: invitation/reset/request delivered
- [ ] `APP_AI_ENABLED=false` globally OR firm AI off for pilot
- [ ] Upload size limits aligned (Spring + Nginx)
- [ ] Subscription ACTIVE or trial extended through pilot end

## Onboarding proven (staging or dry run)

- [ ] New firm registration → login → firm visible
- [ ] Categories available (template or manual)
- [ ] New client created
- [ ] Staff user created with client access
- [ ] Business owner sees client + uploads to request
- [ ] Bank CSV import + duplicate rejection
- [ ] Reconciliation match + ignore
- [ ] Period close with readiness blockers cleared
- [ ] P&L CSV export matches spot check
- [ ] Audit events visible

## Operations

- [ ] Health monitor on `/api/v1/health/ready`
- [ ] Backup failure alert configured
- [ ] Support runbook shared with customer contact
- [ ] Release process: build → migrate → deploy → smoke → rollback documented
- [ ] Data export / exit procedure shared (`PILOT_DATA_EXIT_PLAN.md`)

## Legal & commercial

- [ ] Pilot agreement signed — **LEGAL-REVIEW REQUIRED**
- [ ] Privacy notice / subprocessors list (hosting, email, AI if any)
- [ ] Manual billing process agreed (`PILOT_BILLING_PROCESS.md`)
- [ ] Ledger coexistence option documented per client

## Post go-live (week 1)

- [ ] Weekly check-in scheduled
- [ ] Success metrics baseline captured
- [ ] Platform usage/quota review (users/clients)
