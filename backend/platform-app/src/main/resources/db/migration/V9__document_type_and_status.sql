-- Expand receipt document types and processing statuses for the document inbox.

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_document_type_check;
ALTER TABLE receipts ADD CONSTRAINT receipts_document_type_check
    CHECK (document_type IN (
        'RECEIPT',
        'INVOICE',
        'BANK_SLIP',
        'OTHER',
        'PURCHASE_INVOICE',
        'SALES_INVOICE',
        'BANK_STATEMENT'
    ));

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_status_check;
ALTER TABLE receipts ADD CONSTRAINT receipts_status_check
    CHECK (status IN (
        'UPLOADED',
        'PROCESSING',
        'EXTRACTED',
        'NEEDS_REVIEW',
        'LINKED',
        'REJECTED',
        'FAILED'
    ));

ALTER TABLE receipts DROP CONSTRAINT IF EXISTS receipts_extraction_status_check;
ALTER TABLE receipts ADD CONSTRAINT receipts_extraction_status_check
    CHECK (extraction_status IS NULL OR extraction_status IN (
        'NOT_STARTED',
        'PENDING',
        'PROCESSING',
        'COMPLETED',
        'FAILED',
        'AI_DISABLED'
    ));

CREATE INDEX IF NOT EXISTS idx_receipts_needs_review
    ON receipts (firm_id, client_id, status)
    WHERE deleted_at IS NULL AND status IN ('UPLOADED', 'NEEDS_REVIEW', 'EXTRACTED');
