#!/usr/bin/env bash
# Restore local document volume from a tar.gz archive created by backup-documents-local.sh.
#
# Safeguards:
#   - Requires RESTORE_MODE=recovery
#   - Requires CONFIRM_RESTORE=yes
#   - Requires explicit ARCHIVE_FILE path
#   - Prompts for typed confirmation of the volume name
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

main() {
  load_backup_env
  load_app_env

  [[ "${RESTORE_MODE:-}" == "recovery" ]] || die "Set RESTORE_MODE=recovery to proceed"
  [[ "${CONFIRM_RESTORE:-}" == "yes" ]] || die "Set CONFIRM_RESTORE=yes to proceed"
  [[ -n "${ARCHIVE_FILE:-}" ]] || die "ARCHIVE_FILE is required"
  [[ -f "${ARCHIVE_FILE}" ]] || die "Archive not found: ${ARCHIVE_FILE}"
  [[ "${ARCHIVE_FILE}" != *.tmp ]] || die "Refusing to restore from a temporary/partial archive"

  local volume ts typed
  volume="$(resolve_compose_volume "finance_uploads")"
  ts="$(timestamp_utc)"

  log_warn "This will REPLACE contents of Docker volume: ${volume}"
  log_warn "Coordinate this restore with a PostgreSQL backup from the same window."
  if [[ -n "${RESTORE_TYPED_CONFIRM:-}" ]]; then
    typed="${RESTORE_TYPED_CONFIRM}"
  else
    read -r -p "Type the volume name to confirm: " typed
  fi
  [[ "${typed}" == "${volume}" ]] || die "Confirmation mismatch — aborting"

  verify_tar_gz "${ARCHIVE_FILE}"

  log_info "Stopping backend to release volume handles"
  compose stop backend || true

  log_info "Extracting ${ARCHIVE_FILE} into volume ${volume}"
  if ! docker run --rm \
      -v "${volume}:/data" \
      -v "$(dirname "${ARCHIVE_FILE}"):/backup:ro" \
      alpine:3.20 \
      sh -c "find /data -mindepth 1 -delete && tar -xzf /backup/$(basename "${ARCHIVE_FILE}") -C /data"; then
    die "Document restore failed"
  fi

  log_info "Starting backend"
  compose up -d backend

  log_event "RESTORE_DOCUMENTS" "SUCCESS" "\"volume\":\"${volume}\""
  log_info "Document volume restore completed at ${ts}"
}

main "$@"
