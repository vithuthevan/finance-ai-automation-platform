#!/usr/bin/env python3
"""Read-only demo state snapshot via API (no secrets printed)."""
import json
import sys
import urllib.request
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from seed_demo import Api, PASSWORD, ADMIN_EMAIL, CLIENT_NAME, login, wait_ready  # noqa: E402


def main() -> int:
    api = Api("http://localhost:8080")
    wait_ready(api)
    if not login(api, ADMIN_EMAIL, PASSWORD):
        print("LOGIN_FAILED")
        return 1
    clients_page = api.get("/api/v1/clients") or {}
    clients = clients_page.get("content", clients_page if isinstance(clients_page, list) else [])
    client = next((c for c in clients if CLIENT_NAME in c.get("name", "")), None)
    if not client:
        print("CLIENT_NOT_FOUND")
        return 1
    cid = client["id"]
    expenses = (api.get(f"/api/v1/clients/{cid}/expenses?size=50") or {}).get("content", [])
    income = (api.get(f"/api/v1/clients/{cid}/income?size=50") or {}).get("content", [])
    bank = (api.get(f"/api/v1/clients/{cid}/bank/transactions?size=20") or {}).get("content", [])
    periods = api.get(f"/api/v1/clients/{cid}/periods") or []
    sep = next((p for p in periods if p.get("year") == 2026 and p.get("month") == 9), None)
    pnl = api.get(
        f"/api/v1/clients/{cid}/reports/profit-and-loss?from=2026-09-01&to=2026-09-30"
    )
    summary = api.get(
        f"/api/v1/clients/{cid}/bank/reconciliation/summary?from=2026-09-01&to=2026-09-30"
    )
    requests = (api.get(f"/api/v1/clients/{cid}/document-requests?size=20") or {}).get(
        "content", []
    )
    snap = {
        "client": client.get("name"),
        "expenses_sept": [
            {
                "vendor": e.get("vendor"),
                "amount": e.get("amount"),
                "status": e.get("status"),
                "date": e.get("expenseDate"),
            }
            for e in expenses
            if str(e.get("expenseDate", "")).startswith("2026-09")
        ],
        "income_sept": [
            {
                "amount": i.get("amount"),
                "status": i.get("status"),
                "date": i.get("incomeDate"),
            }
            for i in income
            if str(i.get("incomeDate", "")).startswith("2026-09")
        ],
        "bank_transactions": [
            {
                "desc": t.get("description"),
                "status": t.get("matchStatus"),
                "debit": t.get("debit"),
                "credit": t.get("credit"),
            }
            for t in bank
        ],
        "bank_count": len(bank),
        "reconciliation_percent": summary.get("reconciliationPercent") if summary else None,
        "september_period": sep,
        "document_requests": [
            {"title": r.get("title"), "status": r.get("status")} for r in requests
        ],
        "pnl": pnl,
    }
    print(json.dumps(snap, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
