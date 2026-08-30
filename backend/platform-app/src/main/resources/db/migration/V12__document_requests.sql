CREATE TABLE document_requests (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id               UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id             UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    requested_by          UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assignee_user_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    description           TEXT         NOT NULL,
    document_type         VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    due_date              DATE,
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'UPLOADED', 'COMPLETED', 'CANCELLED')),
    uploaded_document_id  UUID         REFERENCES receipts(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ,
    created_by            UUID,
    updated_by            UUID
);

CREATE INDEX idx_document_requests_client ON document_requests (client_id, status);
CREATE INDEX idx_document_requests_assignee ON document_requests (assignee_user_id, status);
