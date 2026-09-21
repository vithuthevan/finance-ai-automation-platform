-- Phase 1.6: durable outbox worker scheduling.

ALTER TABLE event_outbox
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_event_outbox_due
    ON event_outbox (next_attempt_at)
    WHERE status = 'PENDING';
