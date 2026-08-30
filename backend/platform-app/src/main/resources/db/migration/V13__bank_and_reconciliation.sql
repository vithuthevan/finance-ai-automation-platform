CREATE TABLE bank_imports (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    uploaded_by     UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    file_name       VARCHAR(255) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    row_count       INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IMPORTED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE bank_transactions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    import_id       UUID         NOT NULL REFERENCES bank_imports(id) ON DELETE CASCADE,
    txn_date        DATE         NOT NULL,
    description     VARCHAR(500),
    reference_no    VARCHAR(100),
    debit           NUMERIC(19,4),
    credit          NUMERIC(19,4),
    balance         NUMERIC(19,4),
    match_status    VARCHAR(20)  NOT NULL DEFAULT 'UNMATCHED'
        CHECK (match_status IN ('UNMATCHED', 'SUGGESTED', 'MATCHED', 'BANK_ONLY', 'IGNORED', 'MISSING_RECEIPT')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE reconciliation_matches (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id              UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id            UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    bank_transaction_id  UUID         NOT NULL REFERENCES bank_transactions(id) ON DELETE CASCADE,
    expense_id           UUID         REFERENCES expenses(id) ON DELETE SET NULL,
    income_id            UUID         REFERENCES income(id) ON DELETE SET NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED'
        CHECK (status IN ('SUGGESTED', 'CONFIRMED', 'REJECTED')),
    confirmed_by         UUID         REFERENCES users(id) ON DELETE SET NULL,
    confirmed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID
);

CREATE INDEX idx_bank_txn_client_status ON bank_transactions (client_id, match_status);
CREATE INDEX idx_bank_txn_date ON bank_transactions (client_id, txn_date);
CREATE INDEX idx_recon_bank ON reconciliation_matches (bank_transaction_id);
