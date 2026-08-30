-- V7: append-only business audit trail

CREATE TABLE audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         REFERENCES firms(id) ON DELETE RESTRICT,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actor_user_id   UUID         REFERENCES users(id) ON DELETE SET NULL,
    actor_role      VARCHAR(40),
    action          VARCHAR(80)  NOT NULL,
    resource_type   VARCHAR(40)  NOT NULL,
    resource_id     UUID,
    client_id       UUID         REFERENCES clients(id) ON DELETE SET NULL,
    correlation_id  VARCHAR(100),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(512),
    before_state    JSONB,
    after_state     JSONB,
    metadata        JSONB,
    outcome         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS'
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_log_firm_occurred
    ON audit_log (firm_id, occurred_at DESC);

CREATE INDEX idx_audit_log_firm_resource
    ON audit_log (firm_id, resource_type, resource_id);

CREATE INDEX idx_audit_log_firm_actor
    ON audit_log (firm_id, actor_user_id, occurred_at DESC);

CREATE INDEX idx_audit_log_action
    ON audit_log (firm_id, action, occurred_at DESC);
