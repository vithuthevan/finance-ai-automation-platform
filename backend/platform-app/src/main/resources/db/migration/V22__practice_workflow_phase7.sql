-- Phase 7: notifications, workflow routing, document request reminders, primary accountant.

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS resource_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS resource_id UUID,
    ADD COLUMN IF NOT EXISTS action_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_notifications_firm_user_read
    ON notifications (firm_id, user_id, read_at, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS notification_deliveries (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id  UUID         NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel          VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    attempted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at          TIMESTAMPTZ,
    failure_reason   TEXT
);

CREATE INDEX IF NOT EXISTS idx_notification_deliveries_notification
    ON notification_deliveries (notification_id);

ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS primary_accountant_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_clients_primary_accountant
    ON clients (firm_id, primary_accountant_user_id)
    WHERE primary_accountant_user_id IS NOT NULL;

ALTER TABLE document_requests
    ADD COLUMN IF NOT EXISTS title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reminder_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_document_requests_due_status
    ON document_requests (client_id, status, due_date);

CREATE TABLE IF NOT EXISTS notification_preferences (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    email_document_requested BOOLEAN NOT NULL DEFAULT TRUE,
    email_document_uploaded BOOLEAN NOT NULL DEFAULT TRUE,
    email_period_ready BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);
