# First paid pilot — deployment checklist

## Infrastructure

- [ ] PostgreSQL 16+ (managed), backups enabled and restore tested
- [ ] TLS termination (load balancer or reverse proxy)
- [ ] Application domain + **allowed CORS origins** for the Angular host
- [ ] **SMTP** (when `APP_EMAIL_PROVIDER=smtp`): host, credentials, `MAIL_FROM`
- [ ] **Object storage** if `APP_STORAGE_PROVIDER=s3`: bucket, keys, region
- [ ] **Redis** if running multiple app instances with rate limiting enabled
- [ ] Secrets manager or sealed env injection (no secrets in git)

## Application

- [ ] Deploy backend JAR built from green CI (`./gradlew :platform-app:bootJar`)
- [ ] Flyway migrations applied through **V35** (head checked at startup)
- [ ] `APP_JWT_SECRET` — strong, unique (validated in `prod`)
- [ ] `APP_FRONTEND_BASE_URL` — public UI origin (validated in `prod`)
- [ ] `APP_EMAIL_PROVIDER` — not `log` in production
- [ ] Disable platform bootstrap admin after first admin exists (`APP_PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false`)
- [ ] Frontend built with correct `API_URL` / environment pointing at backend

## Validation gates (before go-live)

- [ ] Backend CI green (Testcontainers suite on GitHub Actions)
- [ ] `npm test` and `npm run build` green
- [ ] E2E: invoice-to-cash, partial payment, bank-to-invoice (see `e2e/tests/`)
- [ ] Manual smoke test: `docs/FIRST_PAID_PILOT_SMOKE_TEST.md`
- [ ] Tenant isolation integration tests pass in CI
- [ ] Invoice PDF + email smoke on staging SMTP

## Post-deploy

- [ ] Confirm audit log writes for financial mutations
- [ ] Confirm chase policy configurable by firm admin
- [ ] Confirm BUSINESS_OWNER cannot mutate AR, reconcile banks, or edit chase policy

See also: `docs/CI_BACKEND.md`, `FIRST_PAID_PILOT_GO_LIVE_CHECKLIST.md`.
