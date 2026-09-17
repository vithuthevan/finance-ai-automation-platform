#!/usr/bin/env bash
# Logical PostgreSQL backup via pg_dump.
# Writes a gzip-compressed, timestamped dump outside the Postgres container.
# Exits non-zero on failure; partial dumps are discarded and never renamed to the final path.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

main() {
  load_backup_env
  load_app_env
  ensure_dirs
  assert_backup_disk_space

  local ts log_file final
  ts="$(timestamp_utc)"
  log_file="${BACKUP_LOG_DIR}/backup-postgres-${ts}.log"
  BACKUP_TMP="${BACKUP_ROOT}/postgres/.finance-platform-${ts}.sql.gz.tmp"
  final="${BACKUP_ROOT}/postgres/finance-platform-${ts}.sql.gz"
  trap 'rm -f "${BACKUP_TMP}"' EXIT

  exec > >(tee -a "${log_file}") 2>&1

  log_event "BACKUP_POSTGRES" "STARTED" "\"db\":\"${POSTGRES_DB}\""
  log_info "Starting PostgreSQL backup (db=${POSTGRES_DB})"

  local cid
  cid="$(postgres_container_id)"
  [[ -n "${cid}" ]] || die "Postgres container not running. Start the stack first."

  if ! compose exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
    die "PostgreSQL is not ready"
  fi

  rm -f "${BACKUP_TMP}"

  log_info "Running pg_dump → ${BACKUP_TMP}"
  if ! compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
      pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" --no-owner --no-acl --verbose \
      | gzip -c > "${BACKUP_TMP}"; then
    log_event "BACKUP_POSTGRES" "FAILED" "\"reason\":\"pg_dump\""
    die "pg_dump failed"
  fi

  verify_pg_dump_gz "${BACKUP_TMP}"
  atomic_finalize "${BACKUP_TMP}" "${final}"
  trap - EXIT
  log_info "Backup completed: ${final} ($(du -h "${final}" | cut -f1))"

  mark_weekly_monthly "${final}" "${ts}" ".sql.gz"
  cleanup_retention "${BACKUP_ROOT}/postgres" "finance-platform-*.sql.gz"

  log_event "BACKUP_POSTGRES" "SUCCESS" "\"file\":\"${final}\""
  log_info "PostgreSQL backup finished successfully"
}

main "$@"
