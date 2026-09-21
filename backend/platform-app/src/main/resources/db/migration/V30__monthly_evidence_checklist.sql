-- Expected month-end evidence checklist per client (operational, not accounting standards).

CREATE TABLE client_monthly_evidence_items (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id             UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id           UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    document_type       VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    required            BOOLEAN      NOT NULL DEFAULT TRUE,
    responsible_party   VARCHAR(20)  NOT NULL DEFAULT 'CLIENT'
        CHECK (responsible_party IN ('CLIENT', 'FIRM')),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX idx_monthly_evidence_client ON client_monthly_evidence_items (client_id, active, sort_order);
CREATE UNIQUE INDEX uq_monthly_evidence_client_title
    ON client_monthly_evidence_items (client_id, lower(title))
    WHERE active = TRUE;
