#!/usr/bin/env bash
# Copy local backups to durable off-server storage.
# Supports AWS CLI (S3-compatible) or rclone.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

main() {
  load_backup_env
  ensure_dirs

  if [[ "${BACKUP_OFFSITE_ENABLED:-false}" != "true" ]]; then
    log_info "BACKUP_OFFSITE_ENABLED is not true — skipping off-server sync"
    log_warn "On-server backups alone are not sufficient for VPS disk failure. Enable off-site sync after deployment."
    exit 0
  fi

  local ts log_file
  ts="$(timestamp_utc)"
  log_file="${BACKUP_LOG_DIR}/backup-offsite-${ts}.log"
  exec > >(tee -a "${log_file}") 2>&1

  log_event "BACKUP_OFFSITE" "STARTED" "\"root\":\"${BACKUP_ROOT}\""
  log_info "Starting off-server backup sync from ${BACKUP_ROOT}"

  if [[ -n "${BACKUP_S3_URI:-}" ]]; then
    command -v aws >/dev/null 2>&1 || die "aws CLI not installed (required for S3 sync)"

    local aws_args=(s3 sync "${BACKUP_ROOT}/" "${BACKUP_S3_URI}" --only-show-errors)
    if [[ -n "${AWS_ENDPOINT_URL:-}" ]]; then
      aws_args+=(--endpoint-url "${AWS_ENDPOINT_URL}")
    fi
    local sse="${BACKUP_S3_SSE:-AES256}"
    if [[ "${sse}" != "none" ]]; then
      aws_args+=(--sse "${sse}")
    fi

    log_info "aws s3 sync → ${BACKUP_S3_URI}"
    if ! aws "${aws_args[@]}"; then
      log_event "BACKUP_OFFSITE" "FAILED" "\"reason\":\"s3_sync\""
      die "Off-server S3 sync failed"
    fi
  elif [[ -n "${RCLONE_REMOTE:-}" ]]; then
    command -v rclone >/dev/null 2>&1 || die "rclone not installed"
    log_info "rclone sync → ${RCLONE_REMOTE}"
    if ! rclone sync "${BACKUP_ROOT}/" "${RCLONE_REMOTE}" --fast-list; then
      log_event "BACKUP_OFFSITE" "FAILED" "\"reason\":\"rclone\""
      die "rclone sync failed"
    fi
  else
    die "Configure BACKUP_S3_URI or RCLONE_REMOTE in backup.env"
  fi

  log_event "BACKUP_OFFSITE" "SUCCESS"
  log_info "Off-server backup sync finished successfully"
}

main "$@"
