ALTER TABLE firms ADD COLUMN IF NOT EXISTS timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Colombo';
ALTER TABLE firms ADD COLUMN IF NOT EXISTS financial_year_start_month INTEGER NOT NULL DEFAULT 4;
ALTER TABLE firms ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE firm_subscriptions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id            UUID         NOT NULL UNIQUE REFERENCES firms(id) ON DELETE CASCADE,
    plan_code          VARCHAR(40)  NOT NULL DEFAULT 'STANDARD',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    max_clients        INTEGER      NOT NULL DEFAULT 50,
    max_users          INTEGER      NOT NULL DEFAULT 15,
    monthly_documents  INTEGER      NOT NULL DEFAULT 2000,
    ai_monthly_allowance INTEGER    NOT NULL DEFAULT 1000,
    storage_bytes      BIGINT       NOT NULL DEFAULT 10737418240,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID
);
