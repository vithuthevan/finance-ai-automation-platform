-- V5: integrity triggers, updated_at, reporting views

CREATE OR REPLACE FUNCTION check_expense_category_type()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM categories c
        WHERE c.id = NEW.category_id
          AND c.category_type IN ('EXPENSE', 'BOTH')
          AND c.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Category % is not valid for expenses', NEW.category_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_expenses_category_type
    BEFORE INSERT OR UPDATE OF category_id ON expenses
    FOR EACH ROW EXECUTE FUNCTION check_expense_category_type();

CREATE OR REPLACE FUNCTION check_income_category_type()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM categories c
        WHERE c.id = NEW.category_id
          AND c.category_type IN ('INCOME', 'BOTH')
          AND c.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Category % is not valid for income', NEW.category_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_income_category_type
    BEFORE INSERT OR UPDATE OF category_id ON income
    FOR EACH ROW EXECUTE FUNCTION check_income_category_type();

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_firms_updated_at
    BEFORE UPDATE ON firms FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_clients_updated_at
    BEFORE UPDATE ON clients FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_receipts_updated_at
    BEFORE UPDATE ON receipts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_expenses_updated_at
    BEFORE UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_income_updated_at
    BEFORE UPDATE ON income FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Reporting: approved ledger union
CREATE VIEW v_approved_transactions AS
SELECT
    e.id,
    e.firm_id,
    e.client_id,
    'EXPENSE'       AS transaction_type,
    e.transaction_date,
    e.amount,
    e.currency_code,
    e.vendor_name   AS party_name,
    e.category_id,
    e.status,
    e.source,
    e.primary_receipt_id,
    e.approved_at,
    e.created_at
FROM expenses e
WHERE e.status = 'APPROVED'

UNION ALL

SELECT
    i.id,
    i.firm_id,
    i.client_id,
    'INCOME'        AS transaction_type,
    i.transaction_date,
    i.amount,
    i.currency_code,
    i.customer_name AS party_name,
    i.category_id,
    i.status,
    i.source,
    i.primary_receipt_id,
    i.approved_at,
    i.created_at
FROM income i
WHERE i.status = 'APPROVED';

-- AI: receipts awaiting human review
CREATE VIEW v_ai_review_queue AS
SELECT
    r.id              AS receipt_id,
    r.firm_id,
    r.client_id,
    r.file_name,
    r.status          AS receipt_status,
    r.extraction_status,
    r.confidence_score,
    r.suggested_type,
    r.suggested_vendor_or_customer,
    r.suggested_date,
    r.suggested_amount,
    r.suggested_category_id,
    r.uploaded_at,
    r.ai_processed_at
FROM receipts r
WHERE r.deleted_at IS NULL
  AND r.extraction_status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM expense_receipts er
      JOIN expenses e ON e.id = er.expense_id AND e.status = 'APPROVED'
      WHERE er.receipt_id = r.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM income_receipts ir
      JOIN income inc ON inc.id = ir.income_id AND inc.status = 'APPROVED'
      WHERE ir.receipt_id = r.id
  );
