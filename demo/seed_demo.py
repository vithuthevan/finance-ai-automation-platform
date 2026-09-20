#!/usr/bin/env python3
"""Seed Harbor Ledger demo tenant for a live Document-to-Close walkthrough.

Idempotent: safe to re-run. Leaves September books mostly open so you can
upload → review → approve → bank import → close live.

Usage:
  python demo/seed_demo.py
  python demo/seed_demo.py --base-url http://localhost:8080
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import uuid
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FILES = ROOT / "files"

PASSWORD = "DemoPass123!"
ADMIN_EMAIL = "priya@harborledger.demo"
ACCOUNTANT_EMAIL = "nimal@harborledger.demo"
OWNER_EMAIL = "amaya@cedarcafe.lk"
AUDITOR_EMAIL = "ravi@harborledger.demo"

FIRM_NAME = "Harbor Ledger Partners"
CLIENT_NAME = "Cedar Café (Pvt) Ltd"


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def request(self, method: str, path: str, body=None, form: dict | None = None, files: dict | None = None):
        url = f"{self.base}{path}"
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if method.upper() in {"POST", "PUT", "PATCH", "DELETE"}:
            headers["Idempotency-Key"] = str(uuid.uuid4())

        data = None
        if files or form:
            boundary = "----FinanceDemoBoundary7MA4YWxkTrZu0gW"
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
            chunks: list[bytes] = []
            if form:
                for key, value in form.items():
                    if value is None:
                        continue
                    chunks.append(f"--{boundary}\r\n".encode())
                    chunks.append(f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode())
                    chunks.append(f"{value}\r\n".encode())
            if files:
                for key, (filename, content, content_type) in files.items():
                    chunks.append(f"--{boundary}\r\n".encode())
                    chunks.append(
                        f'Content-Disposition: form-data; name="{key}"; filename="{filename}"\r\n'.encode()
                    )
                    chunks.append(f"Content-Type: {content_type}\r\n\r\n".encode())
                    chunks.append(content)
                    chunks.append(b"\r\n")
            chunks.append(f"--{boundary}--\r\n".encode())
            data = b"".join(chunks)
        elif body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read()
                if not raw:
                    return None
                ctype = resp.headers.get("Content-Type", "")
                if "application/json" in ctype:
                    return json.loads(raw.decode())
                return raw
        except urllib.error.HTTPError as err:
            detail = err.read().decode(errors="replace")
            raise RuntimeError(f"{method} {path} -> {err.code}: {detail}") from err

    def get(self, path: str):
        return self.request("GET", path)

    def post(self, path: str, body=None, **kwargs):
        return self.request("POST", path, body=body, **kwargs)

    def put(self, path: str, body=None):
        return self.request("PUT", path, body=body)


def wait_ready(api: Api, attempts: int = 60) -> None:
    for i in range(attempts):
        try:
            api.get("/api/v1/health/ready")
            print(f"Backend ready at {api.base}")
            return
        except Exception:
            print(f"Waiting for backend... ({i + 1}/{attempts})")
            time.sleep(3)
    raise SystemExit("Backend did not become ready in time.")


def login(api: Api, email: str, password: str) -> bool:
    try:
        resp = api.post("/api/v1/auth/login", {"email": email, "password": password})
        api.token = resp["accessToken"]
        return True
    except RuntimeError as err:
        if "401" in str(err) or "Invalid" in str(err):
            return False
        raise


def ensure_register(api: Api) -> None:
    if login(api, ADMIN_EMAIL, PASSWORD):
        print(f"Admin already exists: {ADMIN_EMAIL}")
        return
    print("Registering firm...")
    api.token = None
    api.post(
        "/api/v1/auth/register",
        {
            "firmName": FIRM_NAME,
            "registrationNo": "PV-DEMO-001",
            "email": ADMIN_EMAIL,
            "password": PASSWORD,
            "fullName": "Priya Fernando",
        },
    )
    if not login(api, ADMIN_EMAIL, PASSWORD):
        raise SystemExit("Registered firm but could not log in as admin.")
    print(f"Registered and logged in as {ADMIN_EMAIL}")


def find_by(items, key: str, value: str):
    for item in items:
        if str(item.get(key, "")).strip().lower() == value.strip().lower():
            return item
    return None


def ensure_firm_settings(api: Api) -> None:
    api.put(
        "/api/v1/settings/firm",
        {
            "name": FIRM_NAME,
            "currencyCode": "LKR",
            "timezone": "Asia/Colombo",
            "financialYearStartMonth": 4,
            "aiEnabled": False,
        },
    )
    print("Firm settings: LKR / Asia/Colombo / FY start April / AI off")


def ensure_categories(api: Api) -> dict[str, str]:
    existing = api.get("/api/v1/categories") or []
    wanted = [
        ("EXP-FOOD", "Food & beverage supplies", "EXPENSE"),
        ("EXP-UTIL", "Utilities", "EXPENSE"),
        ("EXP-BANK", "Bank charges", "EXPENSE"),
        ("EXP-RENT", "Rent", "EXPENSE"),
        ("INC-SALES", "Café sales", "INCOME"),
    ]
    ids: dict[str, str] = {}
    for code, name, ctype in wanted:
        found = find_by(existing, "code", code)
        if found:
            ids[code] = found["id"]
            continue
        created = api.post(
            "/api/v1/categories",
            {"code": code, "name": name, "categoryType": ctype},
        )
        ids[code] = created["id"]
        print(f"Category created: {code}")
    return ids


def ensure_client(api: Api) -> str:
    clients = api.get("/api/v1/clients") or []
    # list may be PageResponse or array depending on controller
    if isinstance(clients, dict):
        clients = clients.get("content", [])
    found = find_by(clients, "name", CLIENT_NAME)
    if found:
        print(f"Client exists: {CLIENT_NAME}")
        return found["id"]
    created = api.post(
        "/api/v1/clients",
        {
            "name": CLIENT_NAME,
            "businessRegNo": "PV0023456",
            "contactEmail": "accounts@cedarcafe.lk",
        },
    )
    print(f"Client created: {CLIENT_NAME}")
    return created["id"]


def list_users(api: Api) -> list:
    users = api.get("/api/v1/users") or []
    if isinstance(users, dict):
        return users.get("content", [])
    return users


def ensure_users(api: Api, client_id: str, with_auditor: bool) -> dict[str, str]:
    users = list_users(api)
    specs = [
        (ACCOUNTANT_EMAIL, "Nimal Perera", "ACCOUNTANT"),
        (OWNER_EMAIL, "Amaya Silva", "BUSINESS_OWNER"),
    ]
    if with_auditor:
        specs.append((AUDITOR_EMAIL, "Ravi Jay", "AUDITOR"))
    ids: dict[str, str] = {}
    for email, full_name, role in specs:
        found = find_by(users, "email", email)
        if found:
            ids[email] = found["id"]
        else:
            try:
                created = api.post(
                    "/api/v1/users",
                    {
                        "email": email,
                        "password": PASSWORD,
                        "fullName": full_name,
                        "role": role,
                        "clientIds": [client_id],
                        "clientAccess": [
                            {
                                "clientId": client_id,
                                "accessType": "FULL",
                            }
                        ],
                    },
                )
            except RuntimeError as err:
                if "PLAN_USER_LIMIT_REACHED" in str(err):
                    print(f"Skipped {email}: plan user limit reached (Starter allows 3 users)")
                    continue
                raise
            ids[email] = created["id"]
            print(f"User created: {email} ({role})")
            users = list_users(api)

        # Always refresh client access (Users page cannot do this in UI)
        api.put(
            f"/api/v1/users/{ids[email]}/client-access",
            {
                "assignments": [
                    {
                        "clientId": client_id,
                        "accessType": "FULL",
                    }
                ]
            },
        )
    print("Client access granted for created staff/owner users")

    if ACCOUNTANT_EMAIL in ids:
        api.put(
            f"/api/v1/clients/{client_id}/primary-accountant",
            {"accountantUserId": ids[ACCOUNTANT_EMAIL]},
        )
        print("Primary accountant set to Nimal Perera")
    return ids


def ensure_bank_account(api: Api, client_id: str) -> str:
    accounts = api.get(f"/api/v1/clients/{client_id}/bank/accounts") or []
    found = find_by(accounts, "accountName", "Cedar Café Operating")
    if found:
        print("Bank account exists")
        return found["id"]
    created = api.post(
        f"/api/v1/clients/{client_id}/bank/accounts",
        {
            "bankName": "Commercial Bank",
            "accountName": "Cedar Café Operating",
            "maskedAccountNumber": "****4521",
            "currency": "LKR",
        },
    )
    print("Bank account created")
    return created["id"]


def ensure_august_history(api: Api, client_id: str, categories: dict[str, str]) -> None:
    """Seed prior-month approved activity so Trends / dashboard are not empty."""
    expenses = api.get(f"/api/v1/clients/{client_id}/expenses?size=50") or {}
    content = expenses.get("content", expenses if isinstance(expenses, list) else [])
    if any(str(row.get("vendorName", "")).startswith("Demo Seed") for row in content):
        print("August history already seeded")
        return

    exp = api.post(
        f"/api/v1/clients/{client_id}/expenses",
        {
            "transactionDate": "2026-08-12",
            "categoryId": categories["EXP-FOOD"],
            "amount": 9200.00,
            "currencyCode": "LKR",
            "vendorName": "Demo Seed Keells Aug",
            "description": "Prior month supplies (seed)",
            "taxAmount": 0,
            "referenceNo": "SEED-AUG-EXP",
        },
    )
    api.post(f"/api/v1/clients/{client_id}/expenses/{exp['id']}/approve")

    inc = api.post(
        f"/api/v1/clients/{client_id}/income",
        {
            "transactionDate": "2026-08-20",
            "categoryId": categories["INC-SALES"],
            "amount": 45000.00,
            "currencyCode": "LKR",
            "customerName": "Demo Seed Card Aug",
            "description": "Prior month sales (seed)",
            "paymentMethod": "CARD",
            "taxAmount": 0,
            "referenceNo": "SEED-AUG-INC",
        },
    )
    api.post(f"/api/v1/clients/{client_id}/income/{inc['id']}/approve")
    print("August approved history seeded for Trends")


def ensure_open_document_request(api: Api, client_id: str) -> None:
    page = api.get(f"/api/v1/clients/{client_id}/document-requests?status=OPEN&size=20") or {}
    content = page.get("content", [])
    if any("September rent" in str(row.get("title", "") + row.get("description", "")) for row in content):
        print("Open document request already exists")
        return
    api.post(
        f"/api/v1/clients/{client_id}/document-requests",
        {
            "title": "September rent invoice",
            "description": "Please upload the September shop rent invoice for Cedar Café.",
            "documentType": "PURCHASE_INVOICE",
            "dueDate": "2026-09-20",
            "priority": "NORMAL",
            "assigneeUserId": None,
            "periodId": None,
        },
    )
    print("Open document request created for owner demo")


def ensure_review_document(api: Api, client_id: str) -> None:
    """Optional: upload a receipt already waiting in the inbox."""
    page = api.get(f"/api/v1/clients/{client_id}/documents?size=20") or {}
    content = page.get("content", page if isinstance(page, list) else [])
    if any("Keells supplies" in str(row.get("description", "")) for row in content):
        print("Review document already in inbox")
        return

    receipt = FILES / "keells-receipt.pdf"
    if not receipt.exists():
        print("WARNING: keells-receipt.pdf missing; skip inbox seed")
        return
    api.post(
        f"/api/v1/clients/{client_id}/documents",
        form={
            "documentType": "RECEIPT",
            "description": "Keells supplies 5 Sep",
            "allowDuplicate": "false",
        },
        files={
            "file": (receipt.name, receipt.read_bytes(), "application/pdf"),
        },
    )
    print("Receipt uploaded to Documents inbox (ready for Review)")


def print_summary() -> None:
    print(
        f"""
============================================================
 DEMO READY — open http://localhost:4200
============================================================
 ADMIN        {ADMIN_EMAIL} / {PASSWORD}
 ACCOUNTANT   {ACCOUNTANT_EMAIL} / {PASSWORD}
 OWNER        {OWNER_EMAIL} / {PASSWORD}
 AUDITOR      optional (--with-auditor; Starter plan max 3 users)

 Firm         {FIRM_NAME}
 Client       {CLIENT_NAME}
 Currency     LKR

 Sample files
   {FILES / 'keells-receipt.pdf'}
   {FILES / 'bank-sept.csv'}
   {FILES / 'utility-bill.pdf'}

 Suggested live path (admin already logged in):
   1) Documents > Review Keells > Accept as draft > Expenses Approve
   2) Income > create/approve Card sale 18500 on 2026-09-08
   3) Expenses > create/approve CEB 6200 on 2026-09-10
   4) Banking > Import tab > bank-sept.csv > Preview > Import > Confirm matches
   5) Close > Sep 2026 > Open workspace (readiness shows rent request blocker)
   6) Resolve rent request BEFORE close:
        - Option A: Logout > owner uploads utility-bill.pdf > admin completes request
        - Option B: Document Requests > Cancel "September rent invoice" with note
   7) Close > Sep 2026 > Close period (blockers cleared)
   8) Reports > P&L / Trends
============================================================
"""
    )


def ensure_extra_portfolio_clients(api: Api, categories: dict[str, str], accountant_id: str | None) -> None:
    """Additional clients so Month-end Command Center shows a mixed portfolio."""
    specs = [
        ("Green Leaf Café (Pvt) Ltd", "accounts@greenleaf.lk"),
        ("Ocean Traders Lanka", "finance@oceantraders.lk"),
        ("ABC Engineering Services", "admin@abceng.lk"),
        ("Harbor Retail Collective", "ops@harborretail.lk"),
    ]
    clients = api.get("/api/v1/clients") or []
    if isinstance(clients, dict):
        clients = clients.get("content", [])
    for name, email in specs:
        if find_by(clients, "name", name):
            continue
        created = api.post(
            "/api/v1/clients",
            {"name": name, "contactEmail": email, "businessRegNo": f"PV-{name[:3].upper()}-X"},
        )
        cid = created["id"]
        if accountant_id:
            api.put(f"/api/v1/clients/{cid}/primary-accountant", {"accountantUserId": accountant_id})
        clients.append(created)
        print(f"Portfolio client: {name}")

    # Ocean Traders: one draft expense (approval blocker)
    ocean = find_by(clients, "name", "Ocean Traders Lanka")
    if ocean and categories.get("EXP-UTIL"):
        try:
            api.post(
                f"/api/v1/clients/{ocean['id']}/expenses",
                {
                    "description": "Draft courier Sep",
                    "amount": 4500,
                    "transactionDate": "2026-09-12",
                    "categoryId": categories["EXP-UTIL"],
                },
            )
            print("Ocean Traders: draft expense seeded")
        except RuntimeError:
            pass

    # ABC Engineering: bank account but no September import (attention warning)
    abc = find_by(clients, "name", "ABC Engineering Services")
    if abc:
        accounts = api.get(f"/api/v1/clients/{abc['id']}/bank-accounts") or []
        if isinstance(accounts, dict):
            accounts = accounts.get("content", accounts)
        if not accounts:
            api.post(
                f"/api/v1/clients/{abc['id']}/bank-accounts",
                {"name": "Operating current", "currencyCode": "LKR", "accountNumberLast4": "1234"},
            )
            print("ABC Engineering: bank account seeded (import pending)")


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed Finance Platform demo data")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--skip-document", action="store_true", help="Do not pre-upload Keells receipt")
    parser.add_argument(
        "--with-auditor",
        action="store_true",
        help="Also create auditor (needs plan capacity beyond Starter's 3 users)",
    )
    args = parser.parse_args()

    api = Api(args.base_url)
    wait_ready(api)
    ensure_register(api)
    ensure_firm_settings(api)
    categories = ensure_categories(api)
    client_id = ensure_client(api)
    user_ids = ensure_users(api, client_id, with_auditor=args.with_auditor)
    ensure_extra_portfolio_clients(api, categories, user_ids.get(ACCOUNTANT_EMAIL))
    ensure_bank_account(api, client_id)
    ensure_august_history(api, client_id, categories)
    ensure_open_document_request(api, client_id)
    if not args.skip_document:
        ensure_review_document(api, client_id)
    print_summary()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
    except Exception as exc:  # noqa: BLE001
        print(f"SEED FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
