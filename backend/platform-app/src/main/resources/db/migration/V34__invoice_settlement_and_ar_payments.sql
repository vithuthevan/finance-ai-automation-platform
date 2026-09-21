-- Separate document status from settlement status on sales invoices.

ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(20);

UPDATE sales_invoices
SET settlement_status = CASE
    WHEN status IN ('PARTIALLY_PAID') THEN 'PARTIALLY_PAID'
    WHEN status IN ('PAID') THEN 'PAID'
    WHEN status IN ('OVERDUE') THEN 'OVERDUE'
    ELSE 'UNPAID'
END
WHERE settlement_status IS NULL;

UPDATE sales_invoices
SET status = 'ISSUED'
WHERE status IN ('PARTIALLY_PAID', 'PAID', 'OVERDUE');

ALTER TABLE sales_invoices ALTER COLUMN settlement_status SET DEFAULT 'UNPAID';
ALTER TABLE sales_invoices ALTER COLUMN settlement_status SET NOT NULL;

ALTER TABLE sales_invoices DROP CONSTRAINT IF EXISTS sales_invoices_status_check;
ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_status_check
    CHECK (status IN ('DRAFT', 'ISSUED', 'VOID'));

ALTER TABLE sales_invoices ADD CONSTRAINT sales_invoices_settlement_status_check
    CHECK (settlement_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERDUE'));

CREATE INDEX IF NOT EXISTS idx_sales_invoices_firm_settlement
    ON sales_invoices (firm_id, settlement_status, due_date);

ALTER TABLE sales_invoice_lines
    ADD COLUMN IF NOT EXISTS tax_rate_percent NUMERIC(9, 4) NOT NULL DEFAULT 0;

ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED';
ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS source VARCHAR(40) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;
ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);
ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS reversed_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ar_payments DROP CONSTRAINT IF EXISTS ar_payments_status_check;
ALTER TABLE ar_payments ADD CONSTRAINT ar_payments_status_check
    CHECK (status IN ('RECEIVED', 'PARTIALLY_ALLOCATED', 'ALLOCATED', 'REVERSED'));

ALTER TABLE ar_payment_allocations
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE ar_payment_allocations
    DROP CONSTRAINT IF EXISTS chk_ar_alloc_positive;
ALTER TABLE ar_payment_allocations
    ADD CONSTRAINT chk_ar_alloc_positive CHECK (amount > 0);

ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES sales_invoices(id) ON DELETE SET NULL;
ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS payment_id UUID REFERENCES ar_payments(id) ON DELETE SET NULL;

ALTER TABLE reconciliation_match_group_items
    DROP CONSTRAINT IF EXISTS reconciliation_match_group_items_check;
ALTER TABLE reconciliation_match_group_items ADD CONSTRAINT reconciliation_match_group_items_check CHECK (
    (bank_transaction_id IS NOT NULL
        AND expense_id IS NULL AND income_id IS NULL AND invoice_id IS NULL AND payment_id IS NULL)
    OR (bank_transaction_id IS NULL
        AND (expense_id IS NOT NULL OR income_id IS NOT NULL OR invoice_id IS NOT NULL OR payment_id IS NOT NULL))
);
