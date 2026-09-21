# Backend CI runtime

GitHub Actions workflow: `.github/workflows/backend.yml`

## Required runner capabilities

- **Docker** — Testcontainers starts `postgres:16-alpine` for integration tests (`AbstractPostgresIntegrationTest`).
- **JDK 17** (Temurin).

Ubuntu `ubuntu-latest` runners provide Docker by default; no extra service container is required.

## Command

```bash
cd backend
./gradlew :platform-app:test :platform-app:bootJar --no-daemon
```

## Integration test profile (`integrationtest`)

Configured in `platform-app/src/test/resources/application-integrationtest.yml`:

| Area | Value |
|------|--------|
| Database | PostgreSQL via Testcontainers (Flyway enabled, migrations through **V35**) |
| JWT | Test secret in YAML |
| AI | `app.ai.enabled=false`, provider `mock` |
| Email | `app.email.provider=log` (no SMTP) |
| Storage | Local temp directory |
| Auth rate limit | Disabled for tests |

## Unit / smoke context test (`test` profile)

`FinancePlatformApplicationTests` uses in-memory H2 with Flyway disabled and **scheduling disabled** (`spring.task.scheduling.enabled=false`) so PostgreSQL-specific workers do not run on H2.

## Local development without Docker

Integration tests annotated with `@EnabledIf("com.finance.platform.support.PostgresTestContainer#isDockerAvailable")` are **skipped** when Docker is unavailable or fails to start. This is not an application failure.

Run H2-backed tests only:

```bash
./gradlew :platform-app:test --tests "com.finance.platform.FinancePlatformApplicationTests" --no-daemon
```

For full pilot verification, use CI or start Docker locally before `./gradlew :platform-app:test`.

## Environment variables (production)

See `docs/FIRST_PAID_PILOT_DEPLOYMENT_CHECKLIST.md` and `ProductionEnvironmentValidator` / `ProductionJwtSecretValidator` (`prod` profile).
