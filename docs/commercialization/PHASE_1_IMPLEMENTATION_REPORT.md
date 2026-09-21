# Phase 1.2–1.6 implementation report

## Features

- `RateLimitStore` with `InMemoryRateLimitStore` and optional `RedisRateLimitStore`
- Endpoint-specific policies via `app.auth.*` properties
- HTTP 429 + `Retry-After` on `RateLimitedException`
- Multi-instance prod fail/warn when using memory store
- Session revocation on password reset (consistent with change/deactivate)
- Platform admin bootstrap gated by `app.platform.bootstrap-admin-enabled` and empty grants table
- Unique indexes on `bank_imports (bank_account_id, checksum)` and bank row hashes
- Outbox worker: claim with `FOR UPDATE SKIP LOCKED`, backoff, metrics, AI handler

## Migrations

- V31 bank import uniqueness
- V32 outbox worker columns
- V33 commercial operations schema

## Tests

- `InMemoryRateLimitStoreTest` (expiration + enforcement)
- Integration tests: **NOT EXECUTED** in this environment (Docker/Testcontainers not run)

## Blockers

- Redis optional; excluded from autoconfig until `app.auth.rate-limit-store=redis`
