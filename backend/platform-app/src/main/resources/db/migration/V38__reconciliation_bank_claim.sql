-- Slice 6: one durable bank-line reference per invoice-payment reconciliation group item.
-- Expense/income matches use reconciliation_matches partial uniques (V25); AR uses ar_payments (V35).
-- This closes duplicate group-item rows for the same bank transaction under concurrent invoice confirms.

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_group_item_bank_txn
    ON reconciliation_match_group_items (bank_transaction_id)
    WHERE bank_transaction_id IS NOT NULL;
