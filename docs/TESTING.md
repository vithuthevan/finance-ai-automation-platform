# Testing Guide — Finance Platform V1

## Backend

### Stack

- JUnit 5 (via Spring Boot 4.1)
- Spring Boot Test + MockMvc (`BaseWebIntegrationTest`)
- H2 in-memory (`test` profile) for fast integration tests
- Testcontainers PostgreSQL (`integrationtest` profile) for Flyway, native SQL, and tenant suites
- JaCoCo coverage report (`platform-app/build/reports/jacoco/test/html/index.html`)

### Profiles

| Profile | Database | Flyway | Purpose |
|---------|----------|--------|---------|
| `test` | H2 PostgreSQL mode | Off (ddl-auto create-drop) | Fast API/integration tests |
| `integrationtest` | Testcontainers PostgreSQL 16 | On (V1→latest) | Migration + Postgres behavior |

Test reference data (roles, `STARTER`/`PRACTICE` plans) is seeded by `TestReferenceDataConfig` for the `test` profile.

### Run commands

```bash
cd backend
./gradlew :platform-app:test
./gradlew :platform-app:build
./gradlew :platform-app:jacocoTestReport
```

### Key suites

| Suite | Profile | Focus |
|-------|---------|-------|
| `ClientCreationIntegrationTest` | H2 | Tenant-safe client creation |
| `CategoryCreationIntegrationTest` | H2 | Category uniqueness + roles |
| `FinancialLifecycleIntegrationTest` | H2 | DRAFT→APPROVED→VOID, P&L totals |
| `FlywayMigrationIntegrationTest` | Postgres | Clean DB migrations |
| `TenantIsolationIntegrationTest` | Postgres | Cross-firm IDOR |
| `JwtSecurityIntegrationTest` | Postgres | Token edge cases |
| `PlatformAdminSecurityIntegrationTest` | Postgres | Platform boundary |
| `FileUploadSecurityIntegrationTest` | Postgres | Upload rejection |
| `SubscriptionQuotaConcurrencyIntegrationTest` | Postgres | Client quota race |

Postgres suites are **skipped automatically** when Docker is unavailable (`@Testcontainers(disabledWithoutDocker = true)`).

## Frontend

```bash
cd frontend
npm install
npm run build
npm test
```

- Production build: `ng build` → `dist/finance-platform-web`
- Unit tests: Vitest (`src/**/*.spec.ts`)
- Initial coverage: `auth.service.spec.ts` (role/session policy)

## AI / external providers in tests

- `app.ai.enabled=false`
- `app.ai.provider=mock`
- `app.email.provider=log`
- Local temp storage under `java.io.tmpdir`

## Local Docker (optional)

If Docker is installed, Postgres integration tests run automatically. Full compose validation is documented in `PRODUCTION_READINESS.md`.
