#!/usr/bin/env bash
# Restore PostgreSQL from a gzip pg_dump into a NEW/EMPTY database.
#
# Safeguards:
#   - Requires RESTORE_MODE=recovery
#   - Requires CONFIRM_RESTORE=yes
#   - Requires explicit BACKUP_FILE path
#   - Refuses if target DB name does not match EXPECTED_DB_NAME (unless OVERRIDE_DB_CHECK=yes)
#   - Refuses if the target already has firm rows (unless FORCE_RESTORE_NONEMPTY=yes)
#   - Prompts for typed confirmation of database name
#
# NEVER run against production without understanding the impact.
# Prefer a new/empty recovery environment.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

usage() {
  cat <<'EOF'
Usage:
  RESTORE_MODE=recovery CONFIRM_RESTORE=yes BACKUP_FILE=/path/to/backup.sql.gz \
    ./restore-postgres.sh

Environment:
  RESTORE_MODE             Must be "recovery"
  CONFIRM_RESTORE          Must be "yes"
  BACKUP_FILE              Path to .sql.gz pg_dump backup (required)
  TARGET_POSTGRES_DB       Database to restore into (default: POSTGRES_DB from .env)
  EXPECTED_DB_NAME         Safety check — must match TARGET_POSTGRES_DB
  OVERRIDE_DB_CHECK        Set to "yes" to skip DB name check (dangerous)
  FORCE_RESTORE_NONEMPTY   Set to "yes" to restore over a database that already has firms
  SKIP_STOP_BACKEND        Set to "yes" to skip stopping backend container
  RESTORE_TYPED_CONFIRM    Non-interactive: must equal the target database name

This script is intended for recovery/staging environments with an empty database.
EOF
}

read_confirmation() {
  local expected="$1"
  local typed
  if [[ -n "${RESTORE_TYPED_CONFIRM:-}" ]]; then
    typed="${RESTORE_TYPED_CONFIRM}"
  else
    read -r -p "Type the database name to confirm restore: " typed
  fi
  [[ "${typed}" == "${expected}" ]] || die "Confirmation mismatch — aborting"
}

main() {
  load_backup_env
  load_app_env

  [[ "${RESTORE_MODE:-}" == "recovery" ]] || die "Set RESTORE_MODE=recovery to proceed"
  [[ "${CONFIRM_RESTORE:-}" == "yes" ]] || die "Set CONFIRM_RESTORE=yes to proceed"
  [[ -n "${BACKUP_FILE:-}" ]] || die "BACKUP_FILE is required"
  [[ -f "${BACKUP_FILE}" ]] || die "Backup file not found: ${BACKUP_FILE}"
  [[ "${BACKUP_FILE}" != *.tmp ]] || die "Refusing to restore from a temporary/partial backup file"

  local target_db="${TARGET_POSTGRES_DB:-${POSTGRES_DB}}"
  local expected="${EXPECTED_DB_NAME:-${POSTGRES_DB}}"
  assert_pg_ident "${target_db}"
  assert_pg_ident "${expected}"

  if [[ "${OVERRIDE_DB_CHECK:-}" != "yes" && "${target_db}" != "${expected}" ]]; then
    die "Target database '${target_db}' does not match EXPECTED_DB_NAME '${expected}'. Set OVERRIDE_DB_CHECK=yes to override."
  fi

  log_warn "RESTORE TARGET: database '${target_db}' on host Compose stack in ${APP_ROOT}"
  log_warn "Backup file: ${BACKUP_FILE}"
  log_warn "This will DROP and recreate '${target_db}'. Prefer an empty recovery environment."
  read_confirmation "${target_db}"

  local cid
  cid="$(postgres_container_id)"
  [[ -n "${cid}" ]] || die "Postgres container not running"

  if [[ "${SKIP_STOP_BACKEND:-}" != "yes" ]]; then
    log_info "Stopping backend to prevent connections during restore"
    compose stop backend || true
  fi

  log_info "Verifying backup archive"
  verify_pg_dump_gz "${BACKUP_FILE}"

  if [[ "${FORCE_RESTORE_NONEMPTY:-}" != "yes" ]]; then
    local firms
    firms="$(compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
      psql -U "${POSTGRES_USER}" -d "${target_db}" -tAc "SELECT COUNT(*) FROM firms" 2>/dev/null || echo 0)"
    firms="$(echo "${firms}" | tr -d '[:space:]')"
    if [[ "${firms}" =~ ^[0-9]+$ && "${firms}" -gt 0 ]]; then
      die "Target database '${target_db}' already has ${firms} firm(s). Restore into an empty recovery environment, or set FORCE_RESTORE_NONEMPTY=yes"
    fi
  fi

  log_info "Dropping and recreating database ${target_db}"
  compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
    psql -U "${POSTGRES_USER}" -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${target_db}' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS "${target_db}";
CREATE DATABASE "${target_db}" OWNER "${POSTGRES_USER}";
SQL

  log_info "Restoring from ${BACKUP_FILE}"
  if ! gunzip -c "${BACKUP_FILE}" | compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
      psql -U "${POSTGRES_USER}" -d "${target_db}" -v ON_ERROR_STOP=1; then
    die "Restore failed — database may be incomplete"
  fi

  log_info "Verifying flyway_schema_history exists"
  local migration_count
  migration_count="$(compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
    psql -U "${POSTGRES_USER}" -d "${target_db}" -tAc \
    "SELECT COUNT(*) FROM flyway_schema_history" 2>/dev/null || echo "0")"
  migration_count="$(echo "${migration_count}" | tr -d '[:space:]')"
  [[ "${migration_count}" -gt 0 ]] || die "flyway_schema_history is empty — backup may be invalid"

  if [[ "${SKIP_STOP_BACKEND:-}" != "yes" ]]; then
    log_info "Starting backend (Flyway will validate schema)"
    compose up -d backend
  fi

  log_info "Restore completed. Run deploy/backup/verify-restore.sh and the restore verification checklist."
  log_warn "If document storage was also restored, ensure backup timestamps are aligned."
  log_event "RESTORE_POSTGRES" "SUCCESS" "\"db\":\"${target_db}\""
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

main "$@"
