-- Compound indexes for common firm + client + status + date ledger queries.
-- Category uniqueness remains in V8. Existing amount/status CHECKs remain in V4.

CREATE INDEX IF NOT EXISTS idx_expenses_firm_client_status_date
    ON expenses (firm_id, client_id, status, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_income_firm_client_status_date
    ON income (firm_id, client_id, status, transaction_date DESC);

CREATE INDEX IF NOT EXISTS idx_expenses_firm_category
    ON expenses (firm_id, category_id);

CREATE INDEX IF NOT EXISTS idx_income_firm_category
    ON income (firm_id, category_id);
