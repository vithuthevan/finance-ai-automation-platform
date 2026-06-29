-- V3: categories and receipt file metadata (AI-ready)

CREATE TABLE categories (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id        UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id      UUID         REFERENCES clients(id) ON DELETE CASCADE,
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    category_type  VARCHAR(10)  NOT NULL
        CHECK (category_type IN ('EXPENSE', 'INCOME', 'BOTH')),
    parent_id      UUID         REFERENCES categories(id) ON DELETE SET NULL,
    system         BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,

    CONSTRAINT uq_categories_scope_code UNIQUE (firm_id, client_id, code)
);

CREATE INDEX idx_categories_firm_id   ON categories(firm_id);
CREATE INDEX idx_categories_client_id ON categories(client_id);
CREATE INDEX idx_categories_type      ON categories(category_type);
CREATE INDEX idx_categories_active    ON categories(firm_id, category_type) WHERE deleted_at IS NULL;

CREATE TABLE receipts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id          UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id        UUID         NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    uploaded_by      UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    file_name        VARCHAR(255) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    file_size_bytes  BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    checksum_sha256  CHAR(64),

    document_type    VARCHAR(20)  NOT NULL DEFAULT 'RECEIPT'
        CHECK (document_type IN ('RECEIPT', 'INVOICE', 'BANK_SLIP', 'OTHER')),
    status           VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'EXTRACTED', 'LINKED', 'FAILED')),

    -- AI extraction / categorization (embedded metadata)
    extraction_status              VARCHAR(20)
        CHECK (extraction_status IN ('NOT_STARTED', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    confidence_score               NUMERIC(5,4)
        CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1),
    model_version                  VARCHAR(50),
    prompt_template_id             VARCHAR(50),
    suggested_type                 VARCHAR(10)
        CHECK (suggested_type IS NULL OR suggested_type IN ('EXPENSE', 'INCOME')),
    suggested_vendor_or_customer   VARCHAR(200),
    suggested_date                 DATE,
    suggested_amount               NUMERIC(19,4)
        CHECK (suggested_amount IS NULL OR suggested_amount >= 0),
    suggested_category_id          UUID REFERENCES categories(id) ON DELETE SET NULL,
    ocr_text                       TEXT,
    raw_extraction_json            JSONB,
    ai_processed_at                TIMESTAMPTZ,

    uploaded_at      TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,

    CONSTRAINT uq_receipts_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_receipts_client_id  ON receipts(client_id);
CREATE INDEX idx_receipts_firm_id    ON receipts(firm_id);
CREATE INDEX idx_receipts_status     ON receipts(status);
CREATE INDEX idx_receipts_extraction ON receipts(extraction_status) WHERE extraction_status IS NOT NULL;
CREATE INDEX idx_receipts_checksum   ON receipts(client_id, checksum_sha256);
CREATE INDEX idx_receipts_ai_pending ON receipts(client_id, extraction_status)
    WHERE extraction_status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_receipts_raw_json   ON receipts USING GIN (raw_extraction_json);
