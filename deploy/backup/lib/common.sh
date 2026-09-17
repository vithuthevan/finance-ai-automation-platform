#!/usr/bin/env bash
# Shared helpers for backup/restore scripts.
set -euo pipefail

log() {
  local level="$1"
  shift
  local ts
  ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "${ts} [${level}] $*"
}

log_info()  { log "INFO"  "$*"; }
log_warn()  { log "WARN"  "$*"; }
log_error() { log "ERROR" "$*"; }

# Greppable JSON lines for log shippers (same operation/status shape as app StructuredLog).
log_event() {
  local operation="$1"
  local status="$2"
  local extra="${3:-}"
  if [[ -n "${extra}" ]]; then
    log_info "{\"operation\":\"${operation}\",\"level\":\"INFO\",\"status\":\"${status}\",${extra}}"
  else
    log_info "{\"operation\":\"${operation}\",\"level\":\"INFO\",\"status\":\"${status}\"}"
  fi
}

die() {
  log_error "$*"
  exit 1
}

load_backup_env() {
  local lib_dir script_dir repo_root
  lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  script_dir="$(cd "${lib_dir}/.." && pwd)"
  repo_root="$(cd "${script_dir}/../.." && pwd)"
  local env_file="${BACKUP_ENV_FILE:-${script_dir}/backup.env}"

  if [[ -f "${env_file}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${env_file}"
    set +a
  else
    log_warn "backup.env not found at ${env_file}; using defaults"
  fi

  APP_ROOT="${APP_ROOT:-${repo_root}}"
  COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
  ENV_FILE="${ENV_FILE:-.env}"
  BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/finance-platform}"
  BACKUP_LOG_DIR="${BACKUP_LOG_DIR:-/var/log/finance-platform}"
}

load_app_env() {
  local app_env="${APP_ROOT}/${ENV_FILE}"
  [[ -f "${app_env}" ]] || die "Application .env not found: ${app_env}"
  set -a
  # shellcheck disable=SC1090
  source "${app_env}"
  set +a
  : "${POSTGRES_DB:?POSTGRES_DB is required in ${ENV_FILE}}"
  : "${POSTGRES_USER:?POSTGRES_USER is required in ${ENV_FILE}}"
  : "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required in ${ENV_FILE}}"
  assert_pg_ident "${POSTGRES_DB}"
  assert_pg_ident "${POSTGRES_USER}"
}

ensure_dirs() {
  mkdir -p "${BACKUP_ROOT}/postgres" "${BACKUP_ROOT}/documents" "${BACKUP_LOG_DIR}"
}

assert_backup_disk_space() {
  local min_kb="${BACKUP_MIN_FREE_KB:-1048576}"
  local avail
  avail="$(df -Pk "${BACKUP_ROOT}" | awk 'NR==2 {print $4}')"
  [[ "${avail}" =~ ^[0-9]+$ ]] || die "Unable to determine free space on ${BACKUP_ROOT}"
  if [[ "${avail}" -lt "${min_kb}" ]]; then
    die "Insufficient disk space on ${BACKUP_ROOT} (${avail} KB free, need ${min_kb} KB)"
  fi
}

compose() {
  (cd "${APP_ROOT}" && docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@")
}

postgres_container_id() {
  if [[ -n "${POSTGRES_CONTAINER:-}" ]]; then
    echo "${POSTGRES_CONTAINER}"
    return
  fi
  compose ps -q postgres
}

assert_pg_ident() {
  local name="$1"
  [[ "${name}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "Unsafe PostgreSQL identifier: ${name}"
}

timestamp_utc() {
  date -u +"%Y%m%dT%H%M%SZ"
}

iso_from_backup_ts() {
  local ts="$1"
  if [[ "${ts}" =~ ^([0-9]{4})([0-9]{2})([0-9]{2})T([0-9]{2})([0-9]{2})([0-9]{2})Z$ ]]; then
    echo "${BASH_REMATCH[1]}-${BASH_REMATCH[2]}-${BASH_REMATCH[3]}T${BASH_REMATCH[4]}:${BASH_REMATCH[5]}:${BASH_REMATCH[6]}Z"
  else
    die "Invalid backup timestamp: ${ts}"
  fi
}

date_from_backup_ts() {
  local ts="$1"
  local fmt="$2"
  local iso
  iso="$(iso_from_backup_ts "${ts}")"
  if date -u -d "${iso}" "+${fmt}" >/dev/null 2>&1; then
    date -u -d "${iso}" "+${fmt}"
  elif date -u -j -f "%Y-%m-%dT%H:%M:%SZ" "${iso}" "+${fmt}" >/dev/null 2>&1; then
    date -u -j -f "%Y-%m-%dT%H:%M:%SZ" "${iso}" "+${fmt}"
  else
    die "Unable to parse timestamp ${iso}"
  fi
}

atomic_finalize() {
  local tmp="$1"
  local final="$2"
  [[ -s "${tmp}" ]] || die "Temporary backup is empty or missing: ${tmp}"
  mv "${tmp}" "${final}"
}

verify_pg_dump_gz() {
  local file="$1"
  [[ -s "${file}" ]] || die "Backup is empty: ${file}"
  gzip -t "${file}" || die "gzip integrity check failed: ${file}"
  local bytes
  bytes="$(wc -c < "${file}" | tr -d ' ')"
  [[ "${bytes}" -ge 1024 ]] || die "Backup is suspiciously small (${bytes} bytes) and will not be kept: ${file}"

  local ok=0
  set +o pipefail
  if gzip -dc "${file}" | head -n 20 | grep -q "PostgreSQL database dump"; then
    ok=1
  fi
  set -o pipefail
  [[ "${ok}" -eq 1 ]] || die "File is not a PostgreSQL dump (partial/failed backup discarded): ${file}"
}

verify_tar_gz() {
  local file="$1"
  [[ -s "${file}" ]] || die "Archive is empty: ${file}"
  gzip -t "${file}" || die "gzip integrity check failed: ${file}"
  tar -tzf "${file}" >/dev/null || die "tar listing failed: ${file}"
}

resolve_compose_volume() {
  local logical="$1"
  if [[ -n "${DOCUMENTS_VOLUME:-}" && "${DOCUMENTS_VOLUME}" != "auto" && "${logical}" == "finance_uploads" ]]; then
    echo "${DOCUMENTS_VOLUME}"
    return
  fi

  local full
  full="$(compose config 2>/dev/null | awk -v vol="${logical}:" '
    $0 ~ "^  " vol { in_vol=1 }
    in_vol && $1 == "name:" { print $2; exit }
  ')"
  if [[ -n "${full}" ]]; then
    echo "${full}"
    return
  fi

  local dest="${DOCUMENTS_MOUNT:-/data/uploads}"
  local backend_id
  backend_id="$(compose ps -q backend 2>/dev/null || true)"
  if [[ -n "${backend_id}" ]]; then
    full="$(docker inspect -f "{{range .Mounts}}{{if eq .Destination \"${dest}\"}}{{.Name}}{{end}}{{end}}" "${backend_id}")"
    if [[ -n "${full}" ]]; then
      echo "${full}"
      return
    fi
  fi

  full="$(docker volume ls -q --filter "name=${logical}" | tail -n 1 || true)"
  [[ -n "${full}" ]] || die "Could not resolve Docker volume '${logical}'. Set DOCUMENTS_VOLUME in backup.env"
  echo "${full}"
}

cleanup_retention() {
  local dir="$1"
  local pattern="$2"
  local daily="${RETENTION_DAILY:-7}"
  local weekly="${RETENTION_WEEKLY:-4}"
  local monthly="${RETENTION_MONTHLY:-3}"

  log_info "Retention cleanup in ${dir} (daily=${daily}, weekly=${weekly}, monthly=${monthly})"

  find "${dir}" -maxdepth 1 -type f -name "${pattern}" -mtime +"${daily}" \
    ! -name "*-weekly-*" ! -name "*-monthly-*" -print -delete 2>/dev/null || true

  local cutoff_week
  cutoff_week="$(date -u -d "-${weekly} weeks" +%G-W%V 2>/dev/null || date -u -v-"${weekly}"w +%G-W%V)"
  while IFS= read -r -d '' f; do
    local base week
    base="$(basename "${f}")"
    if [[ "${base}" =~ -weekly-([0-9]{4}-W[0-9]{2}) ]]; then
      week="${BASH_REMATCH[1]}"
      if [[ "${week}" < "${cutoff_week}" ]]; then
        log_info "Removing expired weekly backup: ${f}"
        rm -f "${f}"
      fi
    fi
  done < <(find "${dir}" -maxdepth 1 -type f -name "*-weekly-*" -print0 2>/dev/null)

  local cutoff_month
  cutoff_month="$(date -u -d "-${monthly} months" +%Y-%m 2>/dev/null || date -u -v-"${monthly}"m +%Y-%m)"
  while IFS= read -r -d '' f; do
    local base month
    base="$(basename "${f}")"
    if [[ "${base}" =~ -monthly-([0-9]{4}-[0-9]{2}) ]]; then
      month="${BASH_REMATCH[1]}"
      if [[ "${month}" < "${cutoff_month}" ]]; then
        log_info "Removing expired monthly backup: ${f}"
        rm -f "${f}"
      fi
    fi
  done < <(find "${dir}" -maxdepth 1 -type f -name "*-monthly-*" -print0 2>/dev/null)
}

mark_weekly_monthly() {
  local final_path="$1"
  local ts="$2"
  local suffix="$3"

  local dow day week month stem
  dow="$(date_from_backup_ts "${ts}" "%u")"
  day="$(date_from_backup_ts "${ts}" "%d")"
  week="$(date_from_backup_ts "${ts}" "%G-W%V")"
  month="$(date_from_backup_ts "${ts}" "%Y-%m")"
  stem="${final_path%"-${ts}${suffix}"}"

  if [[ "${dow}" == "7" ]]; then
    local weekly="${stem}-weekly-${week}${suffix}"
    cp -a "${final_path}" "${weekly}" 2>/dev/null || cp "${final_path}" "${weekly}"
    log_info "Weekly marker: ${weekly}"
  fi

  if [[ "${day}" == "01" ]]; then
    local monthly="${stem}-monthly-${month}${suffix}"
    cp -a "${final_path}" "${monthly}" 2>/dev/null || cp "${final_path}" "${monthly}"
    log_info "Monthly marker: ${monthly}"
  fi
}
