-- Phase 2: practice work assignments (lightweight ownership metadata).

CREATE TABLE practice_work_assignments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         REFERENCES clients(id) ON DELETE CASCADE,
    source_type     VARCHAR(40)  NOT NULL,
    source_id       UUID         NOT NULL,
    assigned_user_id UUID        REFERENCES users(id) ON DELETE SET NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    due_date        DATE,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_practice_work_source UNIQUE (firm_id, source_type, source_id)
);

CREATE INDEX idx_practice_work_assignee ON practice_work_assignments (firm_id, assigned_user_id, status);

-- Phase 3: automated client chase.

CREATE TABLE client_chase_policies (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    cadence_days    INTEGER[]    NOT NULL DEFAULT ARRAY[0,3,7,10],
    channels        VARCHAR(20)[] NOT NULL DEFAULT ARRAY['EMAIL'],
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE UNIQUE INDEX uq_client_chase_policy_firm_name ON client_chase_policies (firm_id, name);

CREATE TABLE client_chase_runs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    policy_id       UUID         NOT NULL REFERENCES client_chase_policies(id) ON DELETE RESTRICT,
    source_type     VARCHAR(40)  NOT NULL,
    source_id       UUID         NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'SUPPRESSED', 'FAILED')),
    CONSTRAINT uq_client_chase_run_source UNIQUE (firm_id, source_type, source_id)
);

CREATE TABLE client_chase_actions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    run_id          UUID         NOT NULL REFERENCES client_chase_runs(id) ON DELETE CASCADE,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    cadence_day     INTEGER      NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    recipient       VARCHAR(255),
    subject         VARCHAR(255),
    message_summary TEXT,
    suppressed      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_client_chase_action_once ON client_chase_actions (run_id, cadence_day, channel);

-- Phase 4: reconciliation match groups (N:M).

CREATE TABLE reconciliation_match_groups (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUGGESTED'
        CHECK (status IN ('SUGGESTED', 'CONFIRMED', 'REJECTED')),
    match_score     INTEGER,
    confidence      VARCHAR(10),
    notes           TEXT,
    confirmed_by    UUID         REFERENCES users(id) ON DELETE SET NULL,
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE reconciliation_match_group_items (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID         NOT NULL REFERENCES reconciliation_match_groups(id) ON DELETE CASCADE,
    bank_transaction_id UUID     REFERENCES bank_transactions(id) ON DELETE CASCADE,
    expense_id      UUID         REFERENCES expenses(id) ON DELETE SET NULL,
    income_id       UUID         REFERENCES income(id) ON DELETE SET NULL,
    allocated_amount NUMERIC(19,4),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (
        (bank_transaction_id IS NOT NULL AND expense_id IS NULL AND income_id IS NULL)
        OR (bank_transaction_id IS NULL AND (expense_id IS NOT NULL OR income_id IS NOT NULL))
    )
);

CREATE INDEX idx_recon_group_client ON reconciliation_match_groups (client_id, status);

-- Phase 5/6: invoice-to-cash core (separate from income ledger).

CREATE TABLE ar_customers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id       UUID         REFERENCES clients(id) ON DELETE SET NULL,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(255),
    payment_terms_days INTEGER   NOT NULL DEFAULT 30,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX idx_ar_customers_firm ON ar_customers (firm_id, active);

CREATE TABLE sales_invoices (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    customer_id     UUID         NOT NULL REFERENCES ar_customers(id) ON DELETE RESTRICT,
    invoice_number  VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERDUE','VOID')),
    issue_date      DATE,
    due_date        DATE,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'LKR',
    subtotal        NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_total       NUMERIC(19,4) NOT NULL DEFAULT 0,
    total           NUMERIC(19,4) NOT NULL DEFAULT 0,
    notes           TEXT,
    row_version     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_sales_invoice_number UNIQUE (firm_id, invoice_number)
);

CREATE TABLE sales_invoice_lines (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID         NOT NULL REFERENCES sales_invoices(id) ON DELETE CASCADE,
    line_no         INTEGER      NOT NULL,
    description     VARCHAR(500) NOT NULL,
    quantity        NUMERIC(19,4) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(19,4) NOT NULL DEFAULT 0,
    line_total      NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_code        VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sales_invoice_line UNIQUE (invoice_id, line_no)
);

CREATE TABLE ar_payments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    customer_id     UUID         REFERENCES ar_customers(id) ON DELETE SET NULL,
    payment_date    DATE         NOT NULL,
    amount          NUMERIC(19,4) NOT NULL,
    reference       VARCHAR(120),
    unallocated_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE ar_payment_allocations (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID         NOT NULL REFERENCES ar_payments(id) ON DELETE CASCADE,
    invoice_id      UUID         NOT NULL REFERENCES sales_invoices(id) ON DELETE RESTRICT,
    amount          NUMERIC(19,4) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ar_payment_allocation UNIQUE (payment_id, invoice_id)
);

-- Phase 8: automation rules.

CREATE TABLE automation_rules (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    priority        INTEGER      NOT NULL DEFAULT 100,
    conditions_json TEXT         NOT NULL,
    actions_json    TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID
);

CREATE TABLE automation_executions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    rule_id         UUID         NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    input_json      TEXT         NOT NULL,
    matched         BOOLEAN      NOT NULL,
    actions_json    TEXT,
    result_json     TEXT,
    error_summary   VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Phase 10: integration connections.

CREATE TABLE integration_connections (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id         UUID         NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    provider        VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DISCONNECTED',
    credentials_enc TEXT,
    oauth_state     VARCHAR(120),
    sync_cursor     TEXT,
    last_sync_at    TIMESTAMPTZ,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_integration_provider UNIQUE (firm_id, provider)
);

-- Phase 11: billing webhooks.

CREATE TABLE billing_webhook_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider        VARCHAR(40)  NOT NULL,
    external_id     VARCHAR(120) NOT NULL,
    payload         TEXT         NOT NULL,
    signature_valid BOOLEAN,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED','PROCESSED','FAILED','IGNORED')),
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_billing_webhook_external UNIQUE (provider, external_id)
);

-- Phase 12: ROI assumptions.

CREATE TABLE firm_value_assumptions (
    firm_id                     UUID PRIMARY KEY REFERENCES firms(id) ON DELETE CASCADE,
    manual_document_entry_minutes INTEGER NOT NULL DEFAULT 3,
    manual_reconciliation_minutes INTEGER NOT NULL DEFAULT 2,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE firm_invoice_number_sequences (
    firm_id         UUID PRIMARY KEY REFERENCES firms(id) ON DELETE CASCADE,
    next_number     BIGINT NOT NULL DEFAULT 1
);
