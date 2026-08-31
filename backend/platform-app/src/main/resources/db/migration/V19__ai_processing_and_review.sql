-- Phase 4: processing attempts, review outcomes, field confidence, and flags.
-- Original extraction columns remain the AI suggestion snapshot; human edits are not written back.

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_suggested_type_check;
ALTER TABLE receipts ADD CONSTRAINT receipts_suggested_type_check
    CHECK (suggested_type IS NULL OR suggested_type IN ('EXPENSE', 'INCOME', 'UNKNOWN'));

ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_subtotal NUMERIC(19, 4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS line_items_json JSONB;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS supplier_confidence NUMERIC(5, 4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS date_confidence NUMERIC(5, 4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS amount_confidence NUMERIC(5, 4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS tax_confidence NUMERIC(5, 4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS failure_code VARCHAR(50);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS failure_message VARCHAR(500);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS review_outcome VARCHAR(20)
    CHECK (review_outcome IS NULL OR review_outcome IN ('ACCEPTED', 'MODIFIED', 'REJECTED', 'MANUAL'));
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS amount_inconsistency BOOLEAN;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS date_warning VARCHAR(80);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS ai_provider VARCHAR(40);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS input_tokens INTEGER;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS output_tokens INTEGER;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS confirmed_category_id UUID REFERENCES categories(id) ON DELETE SET NULL;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS processing_attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE document_processing_attempts (
    id UUID PRIMARY KEY,
    firm_id UUID NOT NULL,
    receipt_id UUID NOT NULL REFERENCES receipts(id),
    attempt_no INTEGER NOT NULL,
    provider VARCHAR(40),
    model_version VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50),
    failure_message VARCHAR(500),
    duration_ms INTEGER,
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_processing_attempts_receipt
    ON document_processing_attempts (receipt_id, attempt_no DESC);

CREATE INDEX idx_receipts_review_outcome
    ON receipts (firm_id, review_outcome)
    WHERE deleted_at IS NULL AND review_outcome IS NOT NULL;
