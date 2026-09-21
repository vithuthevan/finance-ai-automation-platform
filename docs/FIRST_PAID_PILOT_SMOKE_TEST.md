# First paid pilot — manual smoke test (10–15 minutes)

Use a **staging** firm with demo seed data (no real personal data).

## 1. Sign-in and today view

1. Log in as firm **admin**.
2. Open **Dashboard / Today** — counts load without errors.

## 2. Client and documents

3. Open **Clients** → select a client.
4. **Upload** a document; open **Documents** and complete **review/approve** if AI is enabled.

## 3. Banking

5. **Banking** → import a small **CSV** sample.
6. Open **Reconciliation** → confirm a suggestion or create **unapplied** incoming payment.
7. Verify transaction shows **matched** or payment appears under **AR**.

## 4. Invoice to cash

8. **Accounts receivable** → create **customer** and **invoice**, add lines, **issue**.
9. **Download PDF** and **send email** (staging SMTP / log provider).
10. **Record payment** → **allocate** to invoice; verify **outstanding = 0**, settlement **PAID**.

## 5. Practice operations

11. **Work queue** — open an item; confirm link reaches source screen.
12. **Client chase** — verify runs list; **Firm settings → Client chase** policy saves.
13. **Month-end** command center — readiness loads for current period.

## 6. Authorization spot checks

14. Log in as **BUSINESS_OWNER** — confirm no AR mutation, bank reconciliation, or chase admin.
15. Log in as **accountant** — confirm allowed operational screens only.

Record any failure with steps and screenshot; do not go live with open **P0** financial defects.
