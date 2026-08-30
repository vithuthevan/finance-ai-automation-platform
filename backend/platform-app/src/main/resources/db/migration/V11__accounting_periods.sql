CREATE TABLE accounting_periods (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    period_year     INTEGER      NOT NULL,
    period_month    INTEGER      NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'READY_TO_CLOSE', 'CLOSED', 'REOPENED')),
    closed_by       UUID         REFERENCES users(id) ON DELETE SET NULL,
    closed_at       TIMESTAMPTZ,
    reopen_reason   TEXT,
    reopened_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
    reopened_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_accounting_periods UNIQUE (firm_id, client_id, period_year, period_month)
);

CREATE INDEX idx_periods_client ON accounting_periods (client_id, period_year, period_month);
CREATE INDEX idx_periods_status ON accounting_periods (firm_id, status);
