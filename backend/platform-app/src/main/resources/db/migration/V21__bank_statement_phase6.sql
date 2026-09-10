-- Phase 6: bank accounts, import profiles, enhanced imports/transactions, reconciliation metadata.

CREATE TABLE bank_accounts (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id                 UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id               UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    bank_name               VARCHAR(120) NOT NULL,
    account_name            VARCHAR(120) NOT NULL,
    masked_account_number   VARCHAR(32),
    currency                VARCHAR(3)   NOT NULL DEFAULT 'LKR',
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID
);

CREATE INDEX idx_bank_accounts_firm_client ON bank_accounts (firm_id, client_id);
CREATE INDEX idx_bank_accounts_client_active ON bank_accounts (client_id, active);

CREATE TABLE bank_import_profiles (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id             UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id           UUID         REFERENCES clients(id) ON DELETE CASCADE,
    bank_account_id     UUID         REFERENCES bank_accounts(id) ON DELETE CASCADE,
    profile_name        VARCHAR(120) NOT NULL,
    date_column         INTEGER      NOT NULL DEFAULT 0,
    description_column  INTEGER      NOT NULL DEFAULT 1,
    reference_column    INTEGER      NOT NULL DEFAULT 2,
    debit_column        INTEGER      NOT NULL DEFAULT 3,
    credit_column       INTEGER      NOT NULL DEFAULT 4,
    balance_column      INTEGER      NOT NULL DEFAULT 5,
    amount_column       INTEGER,
    date_format         VARCHAR(40)  NOT NULL DEFAULT 'AUTO',
    header_row          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX idx_bank_import_profiles_client ON bank_import_profiles (client_id);

ALTER TABLE bank_imports
    ADD COLUMN IF NOT EXISTS bank_account_id UUID REFERENCES bank_accounts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS period_from DATE,
    ADD COLUMN IF NOT EXISTS period_to DATE,
    ADD COLUMN IF NOT EXISTS import_status VARCHAR(20) NOT NULL DEFAULT 'IMPORTED',
    ADD COLUMN IF NOT EXISTS imported_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS duplicate_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS document_id UUID REFERENCES receipts(id) ON DELETE SET NULL;

UPDATE bank_imports SET import_status = status WHERE import_status IS NULL OR import_status = 'IMPORTED';

CREATE INDEX IF NOT EXISTS idx_bank_imports_account_checksum ON bank_imports (bank_account_id, checksum);
CREATE INDEX IF NOT EXISTS idx_bank_imports_client_created ON bank_imports (client_id, created_at DESC);

ALTER TABLE bank_transactions
    ADD COLUMN IF NOT EXISTS bank_account_id UUID REFERENCES bank_accounts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS value_date DATE,
    ADD COLUMN IF NOT EXISTS direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS external_row_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'LKR',
    ADD COLUMN IF NOT EXISTS ignore_reason TEXT,
    ADD COLUMN IF NOT EXISTS pending_expense_id UUID REFERENCES expenses(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS pending_income_id UUID REFERENCES income(id) ON DELETE SET NULL;

UPDATE bank_transactions
SET direction = CASE
    WHEN credit IS NOT NULL AND credit > 0 THEN 'CREDIT'
    WHEN debit IS NOT NULL AND debit > 0 THEN 'DEBIT'
    ELSE 'DEBIT'
END
WHERE direction IS NULL;

ALTER TABLE bank_transactions DROP CONSTRAINT IF EXISTS bank_transactions_match_status_check;
ALTER TABLE bank_transactions ADD CONSTRAINT bank_transactions_match_status_check
    CHECK (match_status IN ('UNMATCHED', 'SUGGESTED', 'MATCHED', 'BANK_ONLY', 'IGNORED', 'MISSING_RECEIPT', 'PENDING_APPROVAL'));

CREATE INDEX IF NOT EXISTS idx_bank_txn_account_date ON bank_transactions (bank_account_id, txn_date);
CREATE INDEX IF NOT EXISTS idx_bank_txn_firm_client_date ON bank_transactions (firm_id, client_id, txn_date);
CREATE INDEX IF NOT EXISTS idx_bank_txn_row_hash ON bank_transactions (bank_account_id, external_row_hash);
CREATE INDEX IF NOT EXISTS idx_bank_txn_import ON bank_transactions (import_id);

ALTER TABLE reconciliation_matches
    ADD COLUMN IF NOT EXISTS match_score INTEGER,
    ADD COLUMN IF NOT EXISTS confidence VARCHAR(10),
    ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE INDEX IF NOT EXISTS idx_recon_client_status ON reconciliation_matches (client_id, status);
