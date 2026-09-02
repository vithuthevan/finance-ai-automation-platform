-- Phase 9: persisted platform administrator grants (replaces email allowlist as sole auth).

CREATE TABLE platform_admin_grants (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    granted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    granted_by  UUID         REFERENCES users(id),
    revoked_at  TIMESTAMPTZ,
    revoked_by  UUID         REFERENCES users(id),
    reason      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_admin_grants_user_active ON platform_admin_grants (user_id, active);
