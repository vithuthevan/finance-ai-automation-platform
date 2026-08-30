ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_invoice_no VARCHAR(100);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_due_date DATE;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_currency VARCHAR(3);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_tax_amount NUMERIC(19,4);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_payment_method VARCHAR(20);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS suggested_description TEXT;
