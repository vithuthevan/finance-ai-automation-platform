#!/usr/bin/env bash
# Orchestrates full backup: PostgreSQL, local documents (if applicable), off-server sync.
# Suitable for cron or systemd timer. Exits non-zero if any step fails.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

FAILED=0

run_step() {
  local name="$1"
  shift
  log_info "=== ${name} ==="
  if "$@"; then
    log_info "=== ${name}: OK ==="
  else
    log_error "=== ${name}: FAILED ==="
    FAILED=1
  fi
}

main() {
  load_backup_env
  ensure_dirs
  assert_backup_disk_space

  local ts log_file
  ts="$(timestamp_utc)"
  log_file="${BACKUP_LOG_DIR}/backup-run-${ts}.log"
  exec > >(tee -a "${log_file}") 2>&1

  log_event "BACKUP_RUN" "STARTED"
  log_info "Finance platform backup run started"

  run_step "PostgreSQL backup" "${SCRIPT_DIR}/backup-postgres.sh"
  run_step "Document volume backup" "${SCRIPT_DIR}/backup-documents-local.sh"
  run_step "Off-server sync" "${SCRIPT_DIR}/sync-backups-offsite.sh"

  if [[ "${FAILED}" -ne 0 ]]; then
    log_error "BACKUP_RUN_FAILED event=backup_failed"
    log_event "BACKUP_RUN" "FAILED" "\"log\":\"${log_file}\""
    log_error "Backup run completed with failures — investigate logs in ${BACKUP_LOG_DIR}"
    exit 1
  fi

  log_info "BACKUP_RUN_OK event=backup_succeeded"
  log_event "BACKUP_RUN" "SUCCESS" "\"log\":\"${log_file}\""
  log_info "Backup run completed successfully"
}

main "$@"
