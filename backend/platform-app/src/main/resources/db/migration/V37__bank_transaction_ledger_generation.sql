-- Slice 4: at most one ledger record (expense or income) generated per bank transaction line.

CREATE TABLE bank_transaction_ledger_generations (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id             UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id           UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    bank_transaction_id UUID         NOT NULL REFERENCES bank_transactions(id) ON DELETE CASCADE,
    ledger_kind         VARCHAR(10)  NOT NULL
        CHECK (ledger_kind IN ('EXPENSE', 'INCOME')),
    expense_id          UUID         REFERENCES expenses(id) ON DELETE RESTRICT,
    income_id           UUID         REFERENCES income(id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_bank_txn_ledger_generation UNIQUE (bank_transaction_id),
    CONSTRAINT chk_bank_txn_ledger_target CHECK (
        (ledger_kind = 'EXPENSE' AND expense_id IS NOT NULL AND income_id IS NULL)
        OR (ledger_kind = 'INCOME' AND income_id IS NOT NULL AND expense_id IS NULL)
    )
);

CREATE INDEX idx_bank_txn_ledger_gen_client ON bank_transaction_ledger_generations (client_id);

INSERT INTO bank_transaction_ledger_generations (
    id, firm_id, client_id, bank_transaction_id, ledger_kind, expense_id, created_at, updated_at
)
SELECT gen_random_uuid(), firm_id, client_id, id, 'EXPENSE', pending_expense_id, NOW(), NOW()
FROM bank_transactions
WHERE pending_expense_id IS NOT NULL
  AND pending_income_id IS NULL;

INSERT INTO bank_transaction_ledger_generations (
    id, firm_id, client_id, bank_transaction_id, ledger_kind, income_id, created_at, updated_at
)
SELECT gen_random_uuid(), firm_id, client_id, id, 'INCOME', pending_income_id, NOW(), NOW()
FROM bank_transactions
WHERE pending_income_id IS NOT NULL
  AND pending_expense_id IS NULL;
