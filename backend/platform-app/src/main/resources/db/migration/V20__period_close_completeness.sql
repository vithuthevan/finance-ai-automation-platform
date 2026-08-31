-- Phase 5: period date bounds, review/close metadata, and document-request period linkage.
-- Existing year/month uniqueness remains. Status READY_TO_CLOSE stays in the check for compatibility but is not a persisted workflow state.

ALTER TABLE accounting_periods
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE,
    ADD COLUMN IF NOT EXISTS close_note TEXT,
    ADD COLUMN IF NOT EXISTS review_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_started_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE accounting_periods
SET start_date = make_date(period_year, period_month, 1),
    end_date = (make_date(period_year, period_month, 1) + INTERVAL '1 month - 1 day')::date
WHERE start_date IS NULL OR end_date IS NULL;

ALTER TABLE accounting_periods
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL;

ALTER TABLE accounting_periods
    ADD CONSTRAINT chk_accounting_periods_range CHECK (start_date <= end_date);

CREATE INDEX IF NOT EXISTS idx_periods_firm_client_range
    ON accounting_periods (firm_id, client_id, start_date, end_date);

CREATE INDEX IF NOT EXISTS idx_periods_firm_status_range
    ON accounting_periods (firm_id, status, start_date, end_date);

ALTER TABLE document_requests
    ADD COLUMN IF NOT EXISTS period_id UUID REFERENCES accounting_periods(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_document_requests_period
    ON document_requests (period_id, status)
    WHERE period_id IS NOT NULL;
