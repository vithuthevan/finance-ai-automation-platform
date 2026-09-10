-- Priority 0/1: uniqueness, optimistic versions, outbox, idempotency.

-- Concurrent CONFIRMED matches on the same bank line or ledger row are invalid.
DELETE FROM reconciliation_matches a
USING reconciliation_matches b
WHERE a.status = 'CONFIRMED'
  AND b.status = 'CONFIRMED'
  AND a.bank_transaction_id = b.bank_transaction_id
  AND a.id < b.id;

DELETE FROM reconciliation_matches a
USING reconciliation_matches b
WHERE a.status = 'CONFIRMED'
  AND b.status = 'CONFIRMED'
  AND a.expense_id IS NOT NULL
  AND a.expense_id = b.expense_id
  AND a.id < b.id;

DELETE FROM reconciliation_matches a
USING reconciliation_matches b
WHERE a.status = 'CONFIRMED'
  AND b.status = 'CONFIRMED'
  AND a.income_id IS NOT NULL
  AND a.income_id = b.income_id
  AND a.id < b.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_confirmed_bank_txn
    ON reconciliation_matches (bank_transaction_id)
    WHERE status = 'CONFIRMED';

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_confirmed_expense
    ON reconciliation_matches (expense_id)
    WHERE status = 'CONFIRMED' AND expense_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_confirmed_income
    ON reconciliation_matches (income_id)
    WHERE status = 'CONFIRMED' AND income_id IS NOT NULL;

ALTER TABLE expenses ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE income ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounting_periods ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE bank_transactions ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS event_outbox (
    id             UUID         PRIMARY KEY,
    firm_id        UUID,
    event_type     VARCHAR(80)  NOT NULL,
    aggregate_id   UUID,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    attempt_count  INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_pending
    ON event_outbox (created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_event_outbox_aggregate
    ON event_outbox (event_type, aggregate_id);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id             UUID         PRIMARY KEY,
    firm_id        UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    key_hash       VARCHAR(64)  NOT NULL,
    method         VARCHAR(10)  NOT NULL,
    path           VARCHAR(500) NOT NULL,
    request_hash   VARCHAR(64)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'STARTED'
        CHECK (status IN ('STARTED', 'COMPLETED')),
    status_code    INTEGER,
    response_body  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMPTZ,
    CONSTRAINT uq_idempotency_scope UNIQUE (firm_id, user_id, key_hash)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created ON idempotency_keys (created_at);
