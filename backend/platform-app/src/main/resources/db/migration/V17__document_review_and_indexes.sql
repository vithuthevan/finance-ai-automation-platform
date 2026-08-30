-- Phase 2: review metadata, CREDIT_NOTE type, and document query indexes.

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_document_type_check;
ALTER TABLE receipts ADD CONSTRAINT receipts_document_type_check
    CHECK (document_type IN (
        'RECEIPT',
        'INVOICE',
        'BANK_SLIP',
        'OTHER',
        'PURCHASE_INVOICE',
        'SALES_INVOICE',
        'BANK_STATEMENT',
        'CREDIT_NOTE'
    ));

ALTER TABLE receipts ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS review_note TEXT;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_receipts_firm_client_status_uploaded
    ON receipts (firm_id, client_id, status, uploaded_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_receipts_firm_client_checksum
    ON receipts (firm_id, client_id, checksum_sha256)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_receipts_uploaded_by
    ON receipts (firm_id, uploaded_by, uploaded_at DESC)
    WHERE deleted_at IS NULL;
