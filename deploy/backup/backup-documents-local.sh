#!/usr/bin/env bash
# Archive the local document upload volume (finance_uploads).
# Only needed when APP_STORAGE_PROVIDER=local.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

main() {
  load_backup_env
  load_app_env
  ensure_dirs

  if [[ "${BACKUP_LOCAL_DOCUMENTS:-true}" != "true" ]]; then
    log_info "BACKUP_LOCAL_DOCUMENTS is not true — skipping document volume backup"
    exit 0
  fi

  if [[ "${APP_STORAGE_PROVIDER:-local}" == "s3" ]]; then
    log_info "APP_STORAGE_PROVIDER=s3 — document blobs are in object storage; use provider backup/versioning instead"
    exit 0
  fi

  assert_backup_disk_space

  local ts log_file final volume
  ts="$(timestamp_utc)"
  log_file="${BACKUP_LOG_DIR}/backup-documents-${ts}.log"
  BACKUP_TMP="${BACKUP_ROOT}/documents/.finance-uploads-${ts}.tar.gz.tmp"
  final="${BACKUP_ROOT}/documents/finance-uploads-${ts}.tar.gz"
  volume="$(resolve_compose_volume "finance_uploads")"
  trap 'rm -f "${BACKUP_TMP}"' EXIT

  exec > >(tee -a "${log_file}") 2>&1

  log_event "BACKUP_DOCUMENTS" "STARTED" "\"volume\":\"${volume}\""
  log_info "Starting document volume backup (volume=${volume})"

  docker volume inspect "${volume}" >/dev/null 2>&1 || die "Docker volume not found: ${volume}"

  rm -f "${BACKUP_TMP}"

  # Read volume via ephemeral Alpine container — no need to stop the backend.
  if ! docker run --rm \
      -v "${volume}:/data:ro" \
      -v "${BACKUP_ROOT}/documents:/backup" \
      alpine:3.20 \
      sh -c "tar -czf /backup/.finance-uploads-${ts}.tar.gz.tmp -C /data ."; then
    log_event "BACKUP_DOCUMENTS" "FAILED" "\"reason\":\"tar\""
    die "Document volume archive failed"
  fi

  verify_tar_gz "${BACKUP_TMP}"
  atomic_finalize "${BACKUP_TMP}" "${final}"
  trap - EXIT
  log_info "Document backup completed: ${final} ($(du -h "${final}" | cut -f1))"

  mark_weekly_monthly "${final}" "${ts}" ".tar.gz"
  cleanup_retention "${BACKUP_ROOT}/documents" "finance-uploads-*.tar.gz"

  log_event "BACKUP_DOCUMENTS" "SUCCESS" "\"file\":\"${final}\""
  log_info "Document volume backup finished successfully"
}

main "$@"
