-- Phase 8: SaaS plans, subscription lifecycle, plan change requests.

CREATE TABLE subscription_plans (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(40)  NOT NULL UNIQUE,
    name                    VARCHAR(120) NOT NULL,
    description             TEXT,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    max_clients             INTEGER      NOT NULL,
    max_users               INTEGER      NOT NULL,
    monthly_document_limit  INTEGER      NOT NULL,
    monthly_ai_limit        INTEGER      NOT NULL,
    storage_limit_bytes     BIGINT       NOT NULL,
    features                TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID
);

INSERT INTO subscription_plans (code, name, description, max_clients, max_users, monthly_document_limit, monthly_ai_limit, storage_limit_bytes, features)
VALUES
    ('STARTER', 'Starter', 'Small practice getting started', 10, 3, 500, 100, 2147483648, 'AI_EXTRACTION,BANK_RECONCILIATION,EMAIL_NOTIFICATIONS'),
    ('PRACTICE', 'Practice', 'Growing accounting practice', 50, 10, 3000, 1000, 10737418240, 'AI_EXTRACTION,BANK_RECONCILIATION,ADVANCED_REPORTING,EMAIL_NOTIFICATIONS,AUDITOR_ACCESS'),
    ('PROFESSIONAL', 'Professional', 'Established firm with higher volume', 200, 30, 15000, 5000, 53687091200, 'AI_EXTRACTION,BANK_RECONCILIATION,ADVANCED_REPORTING,EMAIL_NOTIFICATIONS,AUDITOR_ACCESS')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE firm_subscriptions
    ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES subscription_plans(id),
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS current_period_start DATE,
    ADD COLUMN IF NOT EXISTS current_period_end DATE,
    ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

ALTER TABLE firm_subscriptions DROP CONSTRAINT IF EXISTS firm_subscriptions_status_check;
ALTER TABLE firm_subscriptions ADD CONSTRAINT firm_subscriptions_status_check
    CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'));

UPDATE firm_subscriptions fs
SET plan_id = sp.id
FROM subscription_plans sp
WHERE fs.plan_id IS NULL
  AND sp.code = CASE WHEN fs.plan_code = 'STANDARD' THEN 'PRACTICE' ELSE fs.plan_code END;

UPDATE firm_subscriptions fs
SET plan_id = (SELECT id FROM subscription_plans WHERE code = 'STARTER' LIMIT 1)
WHERE fs.plan_id IS NULL;

UPDATE firm_subscriptions
SET current_period_start = date_trunc('month', NOW())::date,
    current_period_end = (date_trunc('month', NOW()) + interval '1 month - 1 day')::date,
    trial_ends_at = COALESCE(trial_ends_at, created_at + interval '14 days')
WHERE current_period_start IS NULL;

CREATE INDEX IF NOT EXISTS idx_firm_subscriptions_status ON firm_subscriptions (status);
CREATE INDEX IF NOT EXISTS idx_firm_subscriptions_period ON firm_subscriptions (current_period_start, current_period_end);

CREATE TABLE plan_change_requests (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE CASCADE,
    requested_by    UUID         NOT NULL REFERENCES users(id),
    current_plan_code VARCHAR(40) NOT NULL,
    requested_plan_code VARCHAR(40) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'CANCELLED')),
    note            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_plan_change_requests_firm ON plan_change_requests (firm_id, status);
