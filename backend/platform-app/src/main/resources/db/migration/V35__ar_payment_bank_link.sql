-- Link AR payments to bank transactions for idempotent bank-to-cash reconciliation.

ALTER TABLE ar_payments
    ADD COLUMN IF NOT EXISTS bank_transaction_id UUID REFERENCES bank_transactions(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ar_payments_bank_txn_active
    ON ar_payments (firm_id, bank_transaction_id)
    WHERE bank_transaction_id IS NOT NULL AND status <> 'REVERSED';
