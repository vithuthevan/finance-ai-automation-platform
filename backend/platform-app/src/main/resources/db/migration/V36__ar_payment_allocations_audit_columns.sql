-- Align V33 tables with BaseEntity auditing columns (hibernate ddl-auto=validate).

-- ar_payment_allocations
ALTER TABLE ar_payment_allocations
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE ar_payment_allocations
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE ar_payment_allocations
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE ar_payment_allocations SET updated_at = created_at WHERE updated_at IS NULL;

-- client_chase_runs
ALTER TABLE client_chase_runs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE client_chase_runs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE client_chase_runs
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE client_chase_runs
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE client_chase_runs SET created_at = started_at WHERE created_at IS NULL;
UPDATE client_chase_runs SET updated_at = COALESCE(completed_at, started_at) WHERE updated_at IS NULL;

-- client_chase_actions
ALTER TABLE client_chase_actions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE client_chase_actions
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE client_chase_actions
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE client_chase_actions SET updated_at = COALESCE(sent_at, created_at) WHERE updated_at IS NULL;

-- reconciliation_match_group_items
ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE reconciliation_match_group_items SET updated_at = created_at WHERE updated_at IS NULL;

-- sales_invoice_lines
ALTER TABLE sales_invoice_lines
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE sales_invoice_lines
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE sales_invoice_lines
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE sales_invoice_lines SET updated_at = created_at WHERE updated_at IS NULL;
