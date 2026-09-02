# Bank statement import & reconciliation

V1 supports **CSV import only**. There is no direct bank API, Open Banking, or credential storage.

The product loop:

```
IMPORT → COMPARE → MATCH → INVESTIGATE DIFFERENCES → RESOLVE → RECONCILE → CLOSE
```

Bank reconciliation answers: **does the bank activity agree with the books?** When it does not, the workflow surfaces missing bookkeeping or missing evidence.

## Bank accounts

- Scoped to firm + client
- Fields: bank name, account name, masked account number, currency, active flag
- No internet banking credentials are stored

## CSV import

1. Select client and bank account
2. Upload CSV exported from the bank
3. Map columns (date, description, reference, debit, credit, balance)
4. Preview valid/invalid/duplicate rows (no transactions persisted on preview)
5. Import commits normalized rows

**Duplicate protection:**

- File SHA-256 checksum blocks re-import of the identical file for the same account
- Per-row hash (`accountId + date + amounts + description + reference`) skips duplicate lines across imports

Original CSV is stored via the existing `FileStorageService`. Parsed rows live in `bank_transactions`.

Optional **import profiles** save column mapping per account (e.g. “Commercial Bank CSV”).

## Reconciliation statuses

| Status | Meaning |
|--------|---------|
| `UNMATCHED` | No suggestion or match |
| `SUGGESTED` | Deterministic match candidate(s) awaiting review |
| `MATCHED` | Accountant confirmed link to approved expense/income |
| `PENDING_APPROVAL` | Draft expense/income created from bank line |
| `IGNORED` | Non-bookkeeping row with required reason |

## Matching (deterministic, no AI)

`ReconciliationSuggestionService` scores APPROVED ledger entries:

- Exact amount: +50 (required for candidate)
- Same day: +25; within 3 days: +15
- Reference overlap: up to +15
- Description/vendor overlap: up to +10

Confidence: HIGH (≥70), MEDIUM (≥45), LOW otherwise.

Direction rules:

- Debit bank lines → expense candidates only
- Credit bank lines → income candidates only

VOID and DRAFT ledger entries are not matched as complete records.

Accountant must **confirm** every match; high confidence does not auto-finalize.

## Unmatched workflow

For each unmatched line, accountants can:

- Confirm/reject suggestions or manually confirm against a chosen expense/income
- Create **DRAFT** expense (debit) or income (credit) pre-filled from the bank line
- Request supporting document (Phase 5 `DocumentRequest`)
- Ignore with reason (audited)

Draft entries created from bank lines stay `PENDING_APPROVAL` until approved and then confirmed.

## Closed periods

Reconciliation-changing operations (confirm, reject, unmatch, ignore, create from bank) call `PeriodCloseService.assertPeriodOpen` for the transaction date.

Read-only views remain available for closed periods.

## Month-end close integration

| Situation | Close behavior |
|-----------|----------------|
| No bank account configured | Bank checks skipped |
| Bank account, no import in period | **WARNING** (`BANK_RECONCILIATION_NOT_STARTED`) |
| Unmatched / suggested / pending-approval bank lines | **BLOCKER** (`BANK_RECONCILIATION_INCOMPLETE`) |

Readiness summary includes `bankTransactions`, `matchedBankTransactions`, `unmatchedBankTransactions`, and `reconciliationPercent` when imports exist.

## Roles

| Role | Access |
|------|--------|
| ADMIN / ACCOUNTANT | Full banking workspace |
| AUDITOR | Read-only transactions, matches, summary |
| BUSINESS_OWNER | No banking UI; may receive document requests from bank workflow |
| UPLOAD_ONLY | No banking access |

## Security

- All queries scoped by `firmId` + `clientId`
- Cross-client/firm matching is rejected
- CSV uploads use secure storage; no path injection

## Limitations (V1)

- CSV only (no PDF/OCR bank statements, no live bank feeds)
- 1:1 confirmed matches (no multi-line split matching)
- No automatic match on expense/income approval after bank-created draft
- Balance validation is not enforced when CSV omits balance column
- Reconciliation export (CSV/XLSX) not included in V1
