# Cloud hosting readiness audit

**Audit date:** 2026-09-21  
**Scope:** Current checked-out working tree (not a git tag). No application or Docker files were changed. Nothing was deployed.

---

## 1. Current version

| Item | Value |
| --- | --- |
| Repository / application | `finance-ai-automation-platform` / Spring Boot app name `finance-platform` |
| Current branch | `main` (tracks `origin/main`) |
| Current commit SHA | `fa0ff4e046bd56cdb7677046251b7a4e9463083f` |
| Recent commits | `fa0ff4e` docs: record successful push in cleanup final report · `8eee25d` docs: add repository cleanup verification · `67a8fac` fix month-end UI accessors · `d609f35` tighten gitignore · `6e6280c` align deployment guides |
| Working tree | **Dirty** — large uncommitted commercial/V2 change set (AR/invoicing, chase, monthly evidence, Redis rate-limit store, Flyway V30–V35, frontend AR/chase pages) |
| Version family | **Enhanced / V2 commercial operating platform**, not Basic/V1 |

**Important:** Git `HEAD` on `origin/main` is Flyway **V29**. This audit describes the **working tree**, whose migration head is **V35**. A `git clone` of GitHub `main` is **not** this version. Do not host this tree and a Basic checkout against the same database.

Other local branch (not audited, not switched): `production-readiness`.

---

## 2. Application architecture

Modular Spring Boot monolith + Angular SPA. Independently running processes in the intended Docker layout:

1. **PostgreSQL 16** (data)
2. **Spring Boot `platform-app` JAR** (HTTP API, Flyway, schedulers, outbox worker — one JVM)
3. **Nginx** (serves Angular; same-origin `/api` reverse proxy)
4. **Host TLS terminator** (Caddy sample; not a Compose service)
5. **Optional Redis** (auth rate limits only; **not** in Compose)
6. **Optional host backup timer** (systemd/cron scripts; not a container)

There is no separate worker container, no Hangfire/Quartz cluster, and no in-repo Traefik.

```
Internet
    ↓
TLS terminator (Caddy / Cloudflare / load balancer)  :80/:443
    ↓
Frontend Nginx (Angular SPA)
    ↓  /api  →  backend:8080
Backend API  (Spring Boot modular monolith)
    ├── PostgreSQL 16  (required)
    ├── Local volume or S3-compatible object storage  (documents)
    ├── SMTP  (required in `prod` profile)
    ├── Optional Redis  (distributed auth rate limit)
    └── Optional OpenAI-compatible AI  (extraction)
```

| Layer | Technology |
| --- | --- |
| Frontend | Angular 20 (`finance-platform-web`), runtime `assets/config.json` |
| Backend | Java 17, Spring Boot 4.1.0, Gradle multi-module → one JAR (`platform-app`) |
| Database | PostgreSQL 16, Flyway, Hibernate `ddl-auto: validate` |
| Cache | Not a general cache. Optional Redis **only** for auth rate limiting |
| Object/file storage | `local` filesystem or S3-compatible (`APP_STORAGE_PROVIDER`) |
| Email | `log` (dev) or `smtp` (Jakarta Mail + STARTTLS) |
| Background jobs | Spring `@Scheduled` in the API process + DB outbox worker |
| AI / external | Optional OpenAI-compatible HTTP extraction |
| Reverse proxy | Nginx in the frontend image; sample host `deploy/Caddyfile` |
| Authentication | JWT access token + httpOnly refresh cookie `fp_refresh`; BCrypt passwords |
| Monitoring | Console logs with MDC; Spring Actuator health; Prometheus metrics |
| Build system | Gradle wrapper (backend); npm / Angular CLI (frontend) |
| Docker | `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `docker-compose.prod.yml` |

Modules in the single backend process: `platform-core`, `module-auth`, `module-finance`, `module-ai`, `module-reporting`, `platform-app`.

---

## 3. Docker readiness

| Artifact | Status |
| --- | --- |
| `backend/Dockerfile` | Present. Multi-stage: download Gradle 9.5.1, `bootJar -x test`, Temurin 17 JRE Alpine, non-root `app` user, `EXPOSE 8080`, wget healthcheck on `/api/v1/health/ready` |
| `frontend/Dockerfile` | Present. Build from **repo root**. Node 22 Alpine → Nginx 1.27 Alpine. Copies `deploy/nginx/*.conf`. `EXPOSE 80`. Runs as **root** (default Nginx) |
| `docker-compose.yml` | Dev stack: Postgres + backend (`dev` profile) + frontend. Publishes **5432, 8080, 4200** |
| `docker-compose.prod.yml` | Prod stack: same services, `prod` profile, **required** `.env`. Postgres **not** published. Backend **8080:8080** and frontend **4200:80** still published to all interfaces |
| `.dockerignore` (root) | Excludes `.git`, `backend`, `docs`, `.env`, `node_modules`, `build` |
| `backend/.dockerignore` | Excludes `.gradle`, `build`, `.env` |
| `frontend/.dockerignore` | Exists, but **does not apply** when build context is repo root (root `.dockerignore` is used) |

**Can it be built with Docker?** By inspection, **yes** on a host with Docker:

```text
docker compose -f docker-compose.prod.yml --env-file .env build
```

or

```text
docker build -f backend/Dockerfile backend
docker build -f frontend/Dockerfile --build-arg NGINX_CONF=deploy/nginx/same-origin.conf .
```

**This audit did not run `docker build` / `docker compose build`:** the local Docker daemon was not running (`npipe:////./pipe/dockerDesktopLinuxEngine` missing).

### Compose / image findings

| Topic | Finding |
| --- | --- |
| Build context | Backend: `./backend`. Frontend: repository root (needs `frontend/` + `deploy/nginx`) |
| Working directory | Backend `/app`. Frontend Nginx default |
| Ports | Backend 8080, frontend 80 (mapped 4200). Prod still publishes 8080 publicly |
| Start command | `java -jar app.jar` / Nginx default |
| Health checks | Backend image + Compose: `/api/v1/health/ready`. Postgres: `pg_isready`. Frontend: **none** |
| Production profile | Prod Compose sets `SPRING_PROFILES_ACTIVE=prod` |
| Environment | Prod Compose requires `.env` (`required: true`) |
| File permissions | Backend `chown app:app` on `/app` and `/data/uploads` |
| Non-root | Backend **yes**. Frontend Nginx **no** |
| Restart | `unless-stopped` on all Compose services |
| Volumes | `postgres_data`, `finance_uploads` |
| Internet during image build | Backend Dockerfile **downloads Gradle** from `services.gradle.org` every build (wrapper files are copied but unused) |
| Redis / Caddy | **Not** Compose services. TLS is host-level |

**Prod Compose problem:** mapping `8080:8080` and `4200:80` on `0.0.0.0` is unsuitable for a public VM once a host proxy exists. Bind `127.0.0.1:4200:80` and do **not** publish 8080 after Caddy is up.

Dev Compose problem: default DB password `change-me`, default JWT, `email=log`, Swagger enabled via `dev` profile, Postgres published on 5432.

---

## 4. Multi-architecture check

Checked against Docker Official Images metadata (eclipse-temurin library file, 2026-09). Local `docker buildx imagetools inspect` was **not** run (daemon down).

| Image | linux/amd64 | linux/arm64 |
| --- | --- | --- |
| `eclipse-temurin:17-jdk-alpine` (backend build) | **YES** | **NO** (Alpine JDK 17 listed **amd64 only**) |
| `eclipse-temurin:17-jre-alpine` (backend runtime) | **YES** | **NO** (Alpine JRE 17 listed **amd64 only**) |
| `node:22-alpine` (frontend build) | YES | YES |
| `nginx:1.27-alpine` (frontend runtime) | YES | YES |
| `postgres:16-alpine` | YES | YES |

**AMD64 compatible: YES**  
**ARM64 compatible: NO** (backend Alpine Temurin). Oracle Cloud Ampere cannot natively pull the current backend base images. Ubuntu/noble Temurin 17 JRE tags **do** include `arm64v8`; that would be a Dockerfile change (not made in this audit).

QEMU amd64 emulation on ARM is theoretically possible and is **not recommended**.

No JNI / Tesseract / platform-native app libraries were found. PDF rendering is pure Java (`openhtmltopdf`). Architecture risk is **base image**, not application natives.

---

## 5. Database readiness

| Item | Detail |
| --- | --- |
| Technology | PostgreSQL 16 |
| Migrations | Flyway `classpath:db/migration` |
| Hibernate | `ddl-auto: validate` in application YAML (does **not** create/drop production schema) |
| H2 | **Test-only** (`application-test.yml`, `create-drop`). Not a production fallback |
| Empty database | Flyway V1…head creates schema, roles, plans. App can start on empty Postgres |
| Upgrade | Forward-only Flyway. No down migrations. Existing DBs receive pending versions at boot |
| Manual SQL | None required outside Flyway files. No undocumented one-off SQL found |
| Destructive DDL | No `DROP TABLE` / `DROP SCHEMA` in migrations |
| Current migration head (working tree) | **V35__ar_payment_bank_link.sql** |
| Current migration head (git `HEAD`) | **V29__auth_security_hardening.sql** |

Uncommitted / working-tree migrations after V29:

- V30 monthly evidence checklist
- V31 bank import uniqueness
- V32 outbox worker lock columns
- V33 commercial operations platform (assignments, chase, AR, invoices, recon groups)
- V34 invoice settlement and AR payments
- V35 AR payment bank link

**Do not apply these to a live Basic database during a casual experiment.** This audit did not run Flyway against any database.

---

## 6. Basic vs new database isolation

**Rule: THIS VERSION MUST HAVE ITS OWN DATABASE.**

Never share one PostgreSQL database (or one `POSTGRES_DB`) between Basic/V1 and this Enhanced/V2 tree.

Reasons:

- Working tree Flyway continues past V29. Starting this app against a Basic DB **upgrades it irreversibly** to V35.
- Basic app (`validate` + V29 entities) cannot run against a V35 schema.
- New tables hold AR invoices, payments, chase runs, monthly evidence, practice assignments.

Use a new database name (example: `finance_platform_v2`) and new volumes. Do not restore a Basic dump into this stack and expect a rollback.

---

## 7. Persistent file storage

Customer documents (receipts, uploads, request attachments) go through `FileStorageService`:

- **local:** `LocalFileStorageService` writes under `APP_STORAGE_LOCAL_ROOT` (Compose: `/data/uploads` on volume `finance_uploads`)
- **s3:** AWS SDK v2; custom `APP_STORAGE_S3_ENDPOINT` supports MinIO / Wasabi / R2-compatible APIs. Azure Blob is not a first-class provider (S3-compatible gateways only)

Invoice PDFs are generated **in memory** (`ByteArrayOutputStream`) and emailed as attachments; they are not separately persisted unless a document upload path stores them.

| Class | Survives restart? | Customer data? |
| --- | --- | --- |
| Document bytes in local/S3 store | Must, if local: **volume or object store required** | Yes |
| Invoice PDF bytes | Transient unless attached/stored | Yes while in memory / email |
| JVM / container temp | No | Avoid relying on `/tmp` |
| Postgres data | Must — volume or managed DB | Yes |

**Docker volume:** supported (`finance_uploads`).  
**S3 / S3-compatible:** supported.  
**Azure Files/Blob:** not native; use S3-compatible or keep local volume.

**Critical:** with `APP_STORAGE_PROVIDER=local`, document bytes live only in the container filesystem unless the Compose volume (or a bind mount) is attached. Losing that volume loses receipts. Prod YAML **defaults storage provider to `s3`** if unset; Compose still allows `local` when `.env` sets it.

---

## 8. Environment variables

Template exists: `.env.example` (placeholders only). Do not commit `.env`.

### Required for `docker-compose.prod.yml` + `prod` profile

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Bundled Postgres |
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | JDBC (Compose overwrites URL to `jdbc:postgresql://postgres:5432/...` when using bundled DB) |
| `APP_JWT_SECRET` | HMAC signing; ≥32 chars; weak/dev values rejected in `prod` |
| `APP_FRONTEND_BASE_URL` | Password-reset and notification links |
| `APP_EMAIL_PROVIDER` | Must **not** be `log` in `prod` |
| `APP_STORAGE_PROVIDER` | `local` or `s3` |
| `APP_AUTH_COOKIE_SECURE` | Should be `true` behind HTTPS |

When `APP_EMAIL_PROVIDER=smtp`: `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` (port default 587, `MAIL_TLS` default true).

When `APP_STORAGE_PROVIDER=s3`: `APP_STORAGE_S3_BUCKET`, `REGION`, `ACCESS_KEY`, `SECRET_KEY` (optional `ENDPOINT`).

### Optional

| Variable | Purpose |
| --- | --- |
| `APP_CORS_ALLOWED_ORIGINS` | Empty = same-origin. Comma-separated exact origins for split UI |
| `APP_AI_ENABLED` / `APP_AI_PROVIDER` / `APP_AI_API_KEY` | Extraction |
| `APP_PLATFORM_ADMIN_BOOTSTRAP_EMAIL` + `APP_PLATFORM_ADMIN_BOOTSTRAP_ENABLED` | One-time platform admin grant |
| `APP_AUTH_EMAIL_VERIFICATION_REQUIRED` | Default **true** in `prod` |
| `APP_AUTH_RATE_LIMIT_STORE` | `memory` (default) or `redis` |
| `APP_MULTI_INSTANCE` | Must be `true` only with Redis store |
| `app.auth.redis.host` / `port` | Redis (not in `.env.example`) |
| `SPRINGDOC_*` | Keep false in production |
| `DOCUMENT_MAX_FILE_SIZE_MB` / multipart 15MB | Upload limits |

### Dangerous development defaults

| Location | Issue |
| --- | --- |
| `application-local.yml` | Hard-coded development JWT secret; Swagger on; Postgres `postgres`/`postgres` on localhost |
| `application-dev.yml` | Datasource default password `postgres`; CORS `http://localhost:4200`; Swagger on |
| `docker-compose.yml` | JWT default `local-docker-compose-jwt-secret-not-for-production`; DB password `change-me`; `APP_EMAIL_PROVIDER=log`; `APP_AUTH_COOKIE_SECURE=false`; bootstrap email `priya@harborledger.demo`; frontend URL `http://localhost:4200`; **5432 published** |
| `application-test.yml` / integration test YAML | Well-known test JWT; H2 create-drop (tests only) |
| `.github/workflows/e2e.yml` | CI-only JWT and DB password (not production) |
| `demo/seed_demo.py` | Public demo users and a shared demo password (see §9) |

No production JWT fallback exists in `application-prod.yml`. Missing `APP_JWT_SECRET` fails property binding.

---

## 9. Secret safety

Repository search found **no** live AWS keys, PEM private keys, or cloud connection strings.

**Known committed non-production secrets / credentials (do not reuse in production):**

- Development JWT defaults in `application-local.yml` and Compose
- Test JWT in test YAML
- Demo accounts in `demo/seed_demo.py` (`priya@harborledger.demo` and related Harbor Ledger emails; shared demo password in that script)
- CI e2e Postgres/JWT placeholders

`.gitignore` protects: `.env`, `.env.*` (with `!.env.example`), `deploy/backup/backup.env`, `data/`, `uploads/`.

**Gaps:** no ignore rules for `*.pem`, `*.p12`, `*.jks`, `*.sql`, `*.sql.gz`, database dumps. `docs/reports/` is ignored (local audit artifacts).

---

## 10. Frontend production readiness

| Item | Detail |
| --- | --- |
| Framework | Angular 20 |
| Production command | `npm run build` → `ng build` (default configuration **production**, hashed output) |
| Output directory | `frontend/dist/finance-platform-web` (browser assets under `browser/`, matching the Dockerfile) |
| Runtime API URL | `fetch('assets/config.json')` — **no rebuild required** to point at another API if the file in the image/host is replaced |
| Same-origin default | `{ "apiBaseUrl": "/api/v1" }` |
| Split-host example | `frontend/src/assets/config.separate-host.example.json` |
| CORS | Backend env `APP_CORS_ALLOWED_ORIGINS`; frontend interceptor rewrites `/api/...` onto `apiBaseUrl` |
| SPA fallback | Nginx `try_files $uri $uri/ /index.html` |

### Build executed in this audit

```text
cd frontend
npm ci     → exit 0 (494 packages; 3 moderate npm audit findings; install-scripts blocked for native optional deps)
npm run build → exit 0
```

Output: `frontend/dist/finance-platform-web` (~1.93 MB initial). Warning NG8107 on `banking.page.ts` optional chaining (non-blocking).

**Model B caveat:** `deploy/nginx/includes/security-headers.conf` sets `Content-Security-Policy ... connect-src 'self'`. A separately hosted API origin will be blocked until CSP is updated. Refresh cookie is `SameSite=Lax` (see §18/§25).

---

## 11. Backend build

Executed (Windows, Java 17 Temurin, Gradle wrapper):

```text
cd backend
.\gradlew.bat clean build
```

| Stage | Result |
| --- | --- |
| Compile all modules | **SUCCESS** |
| `:platform-app:bootJar` | **SUCCESS** |
| `:module-auth:test` / `:module-finance:test` | Ran (unit tests) |
| `:platform-app:test` | **FAILED** — 94 tests, 92 failed, 1 skipped |

Root cause of platform-app test failures: `DockerClientProviderStrategy` — Docker daemon not running. Failures are Spring context load errors (`IllegalStateException` at `DefaultCacheAwareContextLoaderDelegate`), not assertion failures against a running app.

`@EnabledIf(PostgresTestContainer#isDockerAvailable)` is declared on the **abstract** base class. JUnit `@EnabledIf` is **not inherited**, so subclasses still executed and then failed when Testcontainers could not talk to Docker.

**Classification:** **TEST SKIPPED — INFRASTRUCTURE UNAVAILABLE** (Docker), manifested as **test task FAILURE**, not a **compile/JAR BUILD FAILURE**.

Docker image build uses `gradle :platform-app:bootJar --no-daemon -x test`, so image compilation does not depend on this test run.

---

## 12. Application startup

**Not executed.** Safe local/test startup was blocked:

- Docker daemon unavailable → cannot `docker compose up`
- TCP `127.0.0.1:5432` closed → no local Postgres
- TCP `127.0.0.1:8080` closed → no already-running API

Health endpoints were therefore not live-probed. Compose and image metadata define them (see §13).

---

## 13. Health checks

| Endpoint | Auth | Role |
| --- | --- | --- |
| `GET /api/v1/health` | Public | Liveness (always `UP`; no DB) |
| `GET /api/v1/health/ready` | Public | Readiness (JDBC `isValid`; 503 if DB down) |
| `GET /actuator/health` | Public | Spring Boot health (`show-details: never`) |
| `GET /actuator/health/liveness` | Public (`probes.enabled: true`) | K8s-style liveness |
| `GET /actuator/health/readiness` | Public | K8s-style readiness |
| `GET /actuator/prometheus` | **Authenticated** (not in permitAll) | Metrics scrape needs a token or a proxy exception |

| Question | Answer |
| --- | --- |
| Liveness available? | **Yes** (`/api/v1/health` and actuator liveness) |
| Readiness available? | **Yes** (`/api/v1/health/ready` and actuator readiness) |
| Database health? | **Yes** on custom ready + default actuator DB indicator |
| Redis health? | **No** (Redis autoconfig excluded unless rate-limit store is redis; no dedicated probe) |
| Storage health? | **No** |

Load balancers can use `/api/v1/health` (live) and `/api/v1/health/ready` (ready). Compose currently uses **ready** as the container healthcheck, so a DB outage will restart the backend container.

---

## 14. Redis / cache

Redis is **optional**, **not** in Compose, default `APP_AUTH_RATE_LIMIT_STORE=memory`.

| Topic | Detail |
| --- | --- |
| Required? | No for a **single** API instance |
| Production-only? | No; used only when store=`redis` |
| Host / port | `app.auth.redis.host` default `localhost`, port `6379` |
| TLS / password | **Not implemented** in `AuthRedisRateLimitConfiguration` |
| Failure behavior | Redis errors on login/register propagate (not fail-open). No silent disable |
| Multi-instance | `APP_MULTI_INSTANCE=true` + memory store **fails startup in `prod`** (`AuthRateLimitStartupValidator`) |

Default Compose is one backend replica + memory store → **safe**. Horizontal scale without Redis would silently under-enforce rate limits **unless** `APP_MULTI_INSTANCE=true` (which then fails closed in prod). `.env.example` does not document Redis variables.

---

## 15. Email

| Topic | Detail |
| --- | --- |
| Providers | `log` (default / `matchIfMissing`) or `smtp` |
| SMTP | Host/port/username/password/from; `mail.smtp.starttls.enable` from `MAIL_TLS` (default true). Port 587 STARTTLS path. Port 465 implicit SSL is not separately configured |
| Production | `ProductionEnvironmentValidator` **rejects** `APP_EMAIL_PROVIDER=log`. SMTP requires host/user/password/from |
| External SMTP | Yes (any STARTTLS SMTP: SES, SendGrid, Mailgun, Workspace, etc.) |
| False success | **`LoggingEmailService` always “succeeds”** (logs subject/recipient, not body). Callers then record SENT / persist chase actions. **SMTP failures throw**; `NotificationDispatcher` marks FAILED; chase/invoice send roll back on throw |

`prod` also defaults **email verification required**. Without working SMTP, new firms cannot log in unless `APP_AUTH_EMAIL_VERIFICATION_REQUIRED=false`.

Validator runs on `ApplicationReadyEvent` (after the process is already up). An illegal `log` provider may **not** kill the JVM reliably. Treat that as an operational risk (P1).

---

## 16. Background processing

All jobs run **inside the API process** (`@EnableScheduling`).

| Job | Multi-instance |
| --- | --- |
| `OutboxWorker` (3s poll) | **Safe** — `FOR UPDATE SKIP LOCKED` + `locked_by` / `locked_until` |
| `ClientChaseScheduler` (daily 08:00) | **Duplicate risk** — each instance emails |
| `OverdueDocumentRequestScheduler` (daily 08:00) | **Duplicate risk** |
| `SubscriptionMaintenanceScheduler` (07:30) | **Duplicate risk** (trial suspend / notifications) |
| `BusinessMetricsCollector` (60s) | Duplicate DB reads only; low risk |

**One instance:** acceptable. **Multiple instances:** do not enable until schedulers are leader-elected or disabled on standbys. Outbox AI processing is the durable path (`DocumentAiOutboxHandler`).

---

## 17. Reverse proxy

| Included | Role |
| --- | --- |
| Nginx in frontend image | SPA + `/api/` proxy to `http://backend:8080` (`deploy/nginx/same-origin.conf`) |
| `deploy/nginx/frontend-only.conf` | SPA only (split host) |
| `deploy/Caddyfile` | Host TLS reverse proxy to `localhost:4200` |
| Traefik | None |

Cloud deployment needs a TLS terminator in front of port 4200 (or equivalent). **Public exposure should be 80/443 only.** Do not expose 5432, Redis, or 8080 to the internet. Prod Compose today still publishes 8080 and 4200.

---

## 18. HTTPS

Compatible with Cloudflare, Caddy, Nginx+Let’s Encrypt, and cloud load balancers **if**:

- TLS terminates upstream
- `X-Forwarded-Proto` / `X-Forwarded-Host` are set (Nginx sample does this)
- Backend `server.forward-headers-strategy: framework` is set (**yes**)
- `APP_AUTH_COOKIE_SECURE=true`
- `APP_FRONTEND_BASE_URL` is the public `https://` origin

**Gap:** no `server.tomcat.remoteip.trusted-proxies` (or equivalent) restriction. If **8080 is reachable from the internet**, clients can spoof forwarded headers. Bind backend to localhost or an internal network.

HSTS is enabled in Spring Security headers and in the sample Caddyfile; enable HSTS at the TLS terminator when HTTPS is real.

---

## 19. CORS

Implemented in `CorsConfig`: credentials allowed; **wildcard `*` rejected**; empty list = same-origin only.

`APP_CORS_ALLOWED_ORIGINS` is a backend env var — **production frontend URL can be added without rebuilding the backend**. Frontend `config.json` can be swapped without an Angular rebuild (must still be present in the Nginx html tree).

Flags:

- No `*` with credentials (hard-fail)
- Dev/local profiles default CORS to `http://localhost:4200`
- Prod default empty (correct for Model A)
- Refresh-cookie origin check uses the same allowlist

---

## 20. Database backups

| Capability | Status |
| --- | --- |
| `pg_dump` / restore scripts | `deploy/backup/backup-postgres.sh`, `restore-postgres.sh` |
| Local document tar | `backup-documents-local.sh` / restore |
| Offsite sync | Optional S3/rclone (`BACKUP_OFFSITE_ENABLED` default **false**) |
| systemd timer / cron examples | Present, **not installed by default** |
| Automated on Compose up | **No** |
| Restore drill | Documented as **NOT YET VALIDATED** |

Container Postgres **requires** a host backup strategy (volume snapshots are not DR). This audit does **not** mark customer production ready on backups.

---

## 21. Observability

| Signal | Present |
| --- | --- |
| Logs | Console pattern with timestamp, level, thread, `requestId`, `userId`, `firmId` |
| Structured helpers | `StructuredLog` on health/errors |
| Metrics | Micrometer + Prometheus registry; business gauges (firms, users, documents, expenses) |
| Tracing / OpenTelemetry / App Insights | **Not configured** |
| Sensitive logging | Email bodies and reset tokens are not logged by `LoggingEmailService`. Chase may persist message summary in DB. Access tokens should not be logged (no evidence of logging Authorization headers in the paths reviewed) |

Cloud: ship container stdout to the provider log system; scrape Prometheus only via authenticated or internal network.

---

## 22. Resource requirements

Estimates only (no load test).

| Component | Sensible minimum RAM |
| --- | --- |
| Backend JVM | 512 MB–1 GB heap; plan **1–1.5 GB** container |
| PostgreSQL | **512 MB–1 GB** |
| Redis (if used) | **64–128 MB** |
| Frontend Nginx | **32–64 MB** |
| Host TLS / OS | **256 MB+** |
| Disk | **20 GB+** (images, Postgres, uploads, backups) |

| VM size | Verdict |
| --- | --- |
| 1 GB RAM | **Not suitable** (JVM + Postgres) |
| 2 GB | Possible **after** images are built elsewhere; tight; swapping likely |
| 4 GB | **Minimum comfortable** single-VM Compose (matches `docs/VPS_HOSTING.md`) |
| 8 GB+ | Comfortable for demo + on-server `docker compose build` |

Main consumers: Spring Boot JVM and PostgreSQL. On-server Gradle/JDK image builds need extra RAM/CPU during deploy.

---

## 23. Free hosting compatibility

| Option | Compatibility |
| --- | --- |
| Single free VM + Docker Compose | **Yes on x86_64** with 4 GB RAM preferred. ARM free tiers: **not** with current backend Alpine images |
| Oracle Cloud ARM | **No** native (Temurin 17 Alpine amd64-only). Use an **AMD64** shape, or change backend base image (out of scope here) |
| x86 cloud VM | **Yes** |
| Frontend static (Pages/Vercel) | **Yes**, with CSP + cookie SameSite + CORS + `config.json` changes |
| External managed Postgres | **Yes** — set `SPRING_DATASOURCE_*`; skip bundled `postgres` service; Flyway still runs at boot |

---

## 24. Single-server Docker Compose option

```
Cloud VM
├── Caddy or Cloudflare (host)     ← missing from Compose
├── frontend Nginx
├── backend JVM
├── PostgreSQL
└── Redis                          ← optional; not in Compose
```

**SUPPORTED WITH CHANGES**

Why not “SUPPORTED” as-is:

- TLS is not a Compose service
- Prod file publishes 8080
- `prod` profile requires SMTP + public frontend URL
- Backend image is not ARM64
- Redis omitted (OK for one instance)
- This V2 schema is not committed to `origin/main`

For free/demo hosting this is still the intended architecture (`docs/VPS_HOSTING.md`).

---

## 25. Split cloud option

```
Static frontend host
        ↓
Cloud backend container
        ↓
Managed PostgreSQL
        ↓
Managed Redis / object storage
```

**Compatible with configuration changes**, not drop-in:

- Replace `assets/config.json` `apiBaseUrl`
- Set `APP_CORS_ALLOWED_ORIGINS` to the exact UI origin
- Use `frontend-only.conf` or a static host
- Update CSP `connect-src`
- Refresh cookie is `SameSite=Lax` — **cross-site cookie refresh will not work** until SameSite=None; Secure (not changed in this audit). Access JWT in memory can still call the API if CORS is correct; session restore via cookie will fail across sites

---

## 26. Security review for internet exposure

Not a penetration test. Obvious public-hosting issues:

| Area | Status |
| --- | --- |
| Authentication | JWT + hashed refresh tokens; user re-loaded from DB; tenant `firmId` checked |
| Password policy | Min 8, letter + digit (`PasswordPolicy`) — acceptable floor, not strong |
| Rate limiting | Auth endpoints; memory default; 429 + Retry-After |
| Tenant isolation | Repository/service filters; integration tests exist but **not re-run here** (Docker down) |
| Security headers | Spring + Nginx includes |
| CSRF | Disabled (stateless API). Refresh cookie relies on SameSite=Lax + origin check |
| CORS | Explicit origins only |
| Admin bootstrap | Gated; disabled unless flag + empty grants table |
| Default users | None in Flyway. Demo seed creates well-known Harbor Ledger users **if run** |
| Actuator | Health public; Prometheus authenticated; details hidden |
| Swagger | Off in `prod`; **on and permitAll** in `local`/`dev` — do not use dev profile on the internet |
| Debug | No extra debug ports in Compose |
| DB / Redis ports | Prod Compose does not publish 5432. Dev Compose **does**. Redis not in Compose |
| File downloads | Authenticated `DocumentController` `/content`; storage keys server-generated |
| MFA | Not present |
| Forwarded headers | Trusted-proxy list not restricted |

---

## 27. Cloud readiness classification

### CLOUD HOSTABILITY

**HOSTABLE AFTER REQUIRED FIXES**

Docker Compose on an **AMD64** VM can run this stack after filling `.env`, adding TLS, tightening published ports, and deploying **this working tree** (not a clean `origin/main` clone). It is not “hostable now” because ARM images, public 8080, `prod` SMTP/verification, and uncommitted V35 schema are real blockers depending on target.

### PUBLIC DEMO READINESS

**CONDITIONAL**

Allowed only on an isolated AMD64 VM, own database, HTTPS, non-dev JWT, firewall 80/443, and either SMTP or explicitly disabled email verification. Do not run `demo/seed_demo.py` on a host that real customers can reach without rotating those passwords. Do not use `docker-compose.yml` (dev profile + Swagger + published 5432) as the public stack.

### REAL CUSTOMER PRODUCTION READINESS

**NOT READY**

Uncommitted V2 schema, backups not automated/validated, local-disk documents unless S3 is configured, no MFA, duplicate `@Scheduled` jobs if scaled, prod validators on `ApplicationReadyEvent`, Redis TLS/auth missing, ARM image gap, and public port mappings in prod Compose.

These three classifications are independent.

---

## 28. Recommended deployment architecture

### Option A — free / demo

Prefer existing Docker investment on **one AMD64 VM (4 GB RAM, 20+ GB disk)**.

```
Cloud VM (x86_64)
  Caddy or Cloudflare → localhost:4200
  Docker Compose (prod file, after port bind fix)
    frontend (Nginx + Angular)
    backend (Spring Boot)
    PostgreSQL 16
    volume: postgres_data
    volume: finance_uploads   (or S3 even for demo)
```

Skip Redis. Set strong `APP_JWT_SECRET` and DB password. Use SMTP or turn off email verification. **Separate database from any Basic install.** Do not expose 5432/8080.

### Option B — production

Still one region; do not add Kubernetes unless needed.

- TLS at Cloudflare or managed load balancer
- Backend container(s) on a VM/ACS/ECS **without public 8080**
- **Managed PostgreSQL** (automated backups)
- **S3-compatible object storage** for documents
- SMTP provider
- Redis **only** if running more than one API instance (then add AUTH/TLS)
- Frontend: same origin via Nginx **or** static host after cookie/CSP work
- Install `deploy/backup` timer and complete a restore drill
- Ship a **committed** release of this V2 tree first

---

## 29. Required fixes

### P0 — prevents safe cloud deployment of *this* version

1. **Ship a committed artifact.** Working tree ≠ `fa0ff4e`. GitHub `main` will Flyway-stop at V29 and omit AR/chase/evidence code.
2. **Dedicated database** for this schema (V30–V35). Never share with Basic.
3. **Do not use Oracle Cloud ARM** until backend images move off `eclipse-temurin:17-*-alpine` (amd64-only).
4. **Do not publish Postgres or backend 8080** to the internet. Prod Compose currently publishes 8080.
5. **Do not use `docker-compose.yml` (dev) as production** (Swagger, weak defaults, 5432).

### P1 — required before customer data

1. Fill production `.env`: unique DB password, strong JWT, HTTPS cookie, `APP_FRONTEND_BASE_URL`.
2. Real SMTP **or** explicitly document a demo exception to email verification (`prod` defaults verification **on** and forbids `log` email).
3. Persistent document store: Compose volume **and** backups, or S3.
4. Install and test `pg_dump` + offsite sync; perform a restore drill.
5. Bind frontend to localhost and terminate TLS on 443.
6. Confirm `ProductionJwtSecretValidator` / `ProductionEnvironmentValidator` actually abort the process (they listen to `ApplicationReadyEvent`).
7. Keep a **single** backend replica, or add Redis with AUTH/TLS and disable duplicate schedulers.
8. Never run `demo/seed_demo.py` against a customer environment.
9. Restrict forwarded-header trust to the proxy.
10. Split-UI: SameSite/CSP/CORS before going Model B.

### P2 — operational improvements

1. Add frontend container healthcheck; run Nginx as non-root.
2. Stop downloading Gradle from the internet in the Dockerfile; use the wrapper.
3. Document Redis env vars in `.env.example`; add Redis to Compose only when scaling.
4. Ignore `*.pem` / dump files in gitignore.
5. Make `@EnabledIf` skip work on integration test **subclasses**.
6. Prometheus scrape on an internal listener.
7. OpenTelemetry if the host platform expects traces.
8. Stronger password / MFA for customer production.
9. Fix prod Compose port bindings to `127.0.0.1`.
10. Angular NG8107 warning; npm audit (moderate).

Feature ideas (month-end UX, more AR wizards, Stripe) are **out of scope** for this hosting audit.

---

## 30. Summary tables

### Identity

| Field | Value |
| --- | --- |
| Application | finance-platform (`finance-ai-automation-platform`) |
| Branch | `main` |
| Commit | `fa0ff4e046bd56cdb7677046251b7a4e9463083f` (dirty tree) |
| Architecture | Angular → Nginx → Spring Boot → PostgreSQL (+ optional S3/SMTP/AI/Redis) |

### Docker / CPU

| Field | Value |
| --- | --- |
| Docker status | Files present; local daemon **down**; `bootJar` and frontend build **ok**; `gradlew clean build` **failed tests** (Docker) |
| AMD64 | YES |
| ARM64 | NO (backend Alpine Temurin) |

### Data and integrations

| Field | Value |
| --- | --- |
| Database | PostgreSQL 16 + Flyway **V35** (tree) / **V29** (git HEAD) |
| Persistent storage | Local volume **or** S3; customer docs must not stay only in ephemeral container FS |
| Redis | Optional; default memory; safe for 1 instance |
| SMTP | External SMTP supported; `log` forbidden in `prod`; log provider false-succeeds |
| Health checks | Liveness + readiness + DB; no Redis/storage probes |
| Production config | `.env.example` + `application-prod.yml` placeholders; no secret fallbacks in prod |

### Hosting verdicts

| Field | Value |
| --- | --- |
| Approx. minimum resources | **4 GB RAM**, 20 GB disk (2 GB only if images built off-box) |
| Free hosting option | Single AMD64 VM + Compose + Caddy (Option A) |
| Production hosting option | Managed Postgres + S3 + SMTP + TLS; one or few backend containers (Option B) |
| CLOUD HOSTABILITY | **HOSTABLE AFTER REQUIRED FIXES** |
| PUBLIC DEMO READINESS | **CONDITIONAL** |
| REAL CUSTOMER PRODUCTION READINESS | **NOT READY** |

### Exact next steps (no deploy in this audit)

1. Commit or otherwise package **this working tree** as a release; do not deploy dirty `main` from GitHub SHA `fa0ff4e` expecting V35.
2. Provision an **AMD64** VM (≥4 GB) or change Temurin base images before considering ARM.
3. Create a **new** Postgres database/volume for V2.
4. Copy `.env.example` → `.env`; set JWT, DB password, `APP_FRONTEND_BASE_URL`, SMTP (or demo exceptions).
5. Change prod Compose to bind UI on `127.0.0.1:4200` and drop public 8080.
6. Put Caddy/Cloudflare on 80/443; firewall everything else.
7. `docker compose -f docker-compose.prod.yml --env-file .env up --build -d` on that VM.
8. Probe `/api/v1/health` and `/api/v1/health/ready`; register a firm; confirm Flyway V35.
9. Enable backup scripts and run one restore to a throwaway database.
10. Only then consider public demo; customer data waits on P1.

---

*End of audit. No production services were contacted. No application functionality was changed.*
