#!/usr/bin/env bash
# Read-only restore verification against a running recovery/staging stack.
# Does not modify data. Does NOT prove production recovery — record the checklist separately.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

FAILED=0

check() {
  local name="$1"
  local ok="$2"
  if [[ "${ok}" == "1" ]]; then
    log_info "PASS  ${name}"
  else
    log_error "FAIL  ${name}"
    FAILED=1
  fi
}

sql() {
  compose exec -T -e PGPASSWORD="${POSTGRES_PASSWORD}" postgres \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc "$1" 2>/dev/null || echo ""
}

main() {
  load_backup_env
  load_app_env

  log_warn "Restore verification is a drill aid. Status remains NOT YET VALIDATED until an operator signs the runbook checklist."
  log_info "Checking Compose stack in ${APP_ROOT}"

  local health ready
  health="$(curl -sf http://127.0.0.1:8080/api/v1/health 2>/dev/null || true)"
  ready="$(curl -sf http://127.0.0.1:8080/api/v1/health/ready 2>/dev/null || true)"
  check "GET /api/v1/health returns UP" "$([[ "${health}" == *'"status":"UP"'* || "${health}" == *'"status": "UP"'* ]] && echo 1 || echo 0)"
  check "GET /api/v1/health/ready returns database UP" "$([[ "${ready}" == *'"database":"UP"'* || "${ready}" == *'"database": "UP"'* ]] && echo 1 || echo 0)"

  local firms users clients expenses income receipts periods audit flyway matches imports
  firms="$(sql "SELECT COUNT(*) FROM firms" | tr -d '[:space:]')"
  users="$(sql "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL" | tr -d '[:space:]')"
  clients="$(sql "SELECT COUNT(*) FROM clients WHERE deleted_at IS NULL" | tr -d '[:space:]')"
  expenses="$(sql "SELECT COUNT(*) FROM expenses" | tr -d '[:space:]')"
  income="$(sql "SELECT COUNT(*) FROM income" | tr -d '[:space:]')"
  receipts="$(sql "SELECT COUNT(*) FROM receipts WHERE deleted_at IS NULL" | tr -d '[:space:]')"
  periods="$(sql "SELECT COUNT(*) FROM accounting_periods" | tr -d '[:space:]')"
  audit="$(sql "SELECT COUNT(*) FROM audit_log" | tr -d '[:space:]')"
  flyway="$(sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true" | tr -d '[:space:]')"
  matches="$(sql "SELECT COUNT(*) FROM reconciliation_matches" | tr -d '[:space:]')"
  imports="$(sql "SELECT COUNT(*) FROM bank_imports" | tr -d '[:space:]')"

  log_info "counts firms=${firms} users=${users} clients=${clients} expenses=${expenses} income=${income} receipts=${receipts} periods=${periods} audit_log=${audit} flyway=${flyway} recon_matches=${matches} bank_imports=${imports}"

  check "firms table has rows" "$([[ "${firms}" =~ ^[0-9]+$ && "${firms}" -gt 0 ]] && echo 1 || echo 0)"
  check "users table has rows" "$([[ "${users}" =~ ^[0-9]+$ && "${users}" -gt 0 ]] && echo 1 || echo 0)"
  check "clients table has rows" "$([[ "${clients}" =~ ^[0-9]+$ && "${clients}" -gt 0 ]] && echo 1 || echo 0)"
  check "flyway_schema_history has successful migrations" "$([[ "${flyway}" =~ ^[0-9]+$ && "${flyway}" -gt 0 ]] && echo 1 || echo 0)"
  check "audit_log is queryable" "$([[ "${audit}" =~ ^[0-9]+$ ]] && echo 1 || echo 0)"
  check "accounting_periods is queryable" "$([[ "${periods}" =~ ^[0-9]+$ ]] && echo 1 || echo 0)"
  check "expenses is queryable" "$([[ "${expenses}" =~ ^[0-9]+$ ]] && echo 1 || echo 0)"
  check "income is queryable" "$([[ "${income}" =~ ^[0-9]+$ ]] && echo 1 || echo 0)"

  local latest
  latest="$(sql "SELECT version || ' ' || description FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1" | tr -d '\r')"
  log_info "Latest Flyway migration: ${latest}"

  if [[ "${APP_STORAGE_PROVIDER:-local}" != "s3" ]]; then
    local key
    key="$(sql "SELECT storage_key FROM receipts WHERE deleted_at IS NULL AND storage_key IS NOT NULL LIMIT 1" | tr -d '[:space:]')"
    if [[ -n "${key}" ]]; then
      local backend_id
      backend_id="$(compose ps -q backend || true)"
      if [[ -n "${backend_id}" ]] && docker exec "${backend_id}" test -f "/data/uploads/${key}"; then
        check "sample receipt file exists at storage_key" 1
      else
        check "sample receipt file exists at storage_key (${key})" 0
      fi
    else
      log_warn "No receipt storage_key to check (empty document set is OK for a fresh tenant)"
    fi
  else
    log_info "APP_STORAGE_PROVIDER=s3 — confirm object versions in the document bucket separately"
  fi

  if [[ "${FAILED}" -ne 0 ]]; then
    log_event "RESTORE_VERIFY" "FAILED"
    log_error "Verification failed — see FAIL lines above. NOT YET VALIDATED."
    exit 1
  fi

  log_event "RESTORE_VERIFY" "SUCCESS"
  log_info "Automated checks passed. Operator must still complete the runbook checklist (login, amounts, document open). NOT YET VALIDATED as a production drill until signed off."
}

main "$@"
