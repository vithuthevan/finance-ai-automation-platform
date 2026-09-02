# Production Readiness Checklist — V1

Use this checklist before deploying to a real environment. Phase 9 validates build/test locally only.

## Secrets & configuration

- [ ] Set strong `JWT_SECRET` (≥ 32 bytes, base64-encoded recommended)
- [ ] Configure PostgreSQL credentials (not H2)
- [ ] Set `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` only for first admin bootstrap, then revoke bootstrap env
- [ ] Configure object storage (local path or S3-compatible) with restricted IAM
- [ ] Set `APP_AI_ENABLED` and provider keys only if AI is required
- [ ] SMTP credentials for email notifications (or `log` provider in dev)

## Database

- [ ] Automated PostgreSQL backups (daily minimum, point-in-time if available)
- [ ] Flyway migrations applied on deploy (`V1` → latest, currently `V24`)
- [ ] Verify migration on staging before production
- [ ] Connection pooling sized for expected load

## HTTPS & network

- [ ] TLS terminated at load balancer or reverse proxy
- [ ] `Strict-Transport-Security` enabled
- [ ] CORS `APP_CORS_ALLOWED_ORIGINS` set to real frontend origins (no `*` in production)
- [ ] Rate limiting at proxy for `/api/v1/auth/login` (supplement in-app limiter)

## Application

- [ ] Health check: `/actuator/health` (expose only safe details)
- [ ] Graceful failure if DB/JWT secret missing (`prod` profile validators)
- [ ] `spring.jpa.open-in-view=false` (default) — ensure lazy associations fetched in services/filters
- [ ] Log level INFO in production; no secrets/PII in logs

## Storage & uploads

- [ ] Upload size limits aligned: Spring multipart, app config, Nginx `client_max_body_size`
- [ ] Storage root/S3 bucket not web-accessible directly
- [ ] Backup/versioning for document objects

## Email

- [ ] `APP_FRONTEND_BASE_URL` set for links in notifications
- [ ] Email failures must not roll back accounting (already async/logged)

## Monitoring

- [ ] Centralized logs with request correlation (optional `X-Request-ID`)
- [ ] Alerts on: migration failure, repeated 5xx, auth rate-limit spikes, AI processing failures

## Subscription & platform admin

- [ ] Seed/check `subscription_plans` data
- [ ] Grant platform admins via API after bootstrap; audit grant/revoke
- [ ] Document downgrade/suspension behavior for support staff

## Docker (optional)

- [ ] Build backend and frontend images locally before CI deploy
- [ ] Docker not available in Phase 9 CI environment on this machine — use CI runner with Docker for Postgres suites

## Not in V1 scope

- Cloud deployment automation (Phase 10)
- Distributed rate limiting / Redis
- WAF / antivirus scanning
- Formal penetration test
