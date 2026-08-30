-- V4: expenses, income, and receipt evidence links

CREATE TABLE expenses (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id                  UUID          NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id                UUID          NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    category_id              UUID          NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,

    transaction_date         DATE          NOT NULL,
    amount                   NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_code            CHAR(3)       NOT NULL DEFAULT 'LKR',
    vendor_name              VARCHAR(200)  NOT NULL,
    description              TEXT,
    tax_amount               NUMERIC(19,4) CHECK (tax_amount IS NULL OR tax_amount >= 0),
    reference_no             VARCHAR(100),

    status                   VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'VOID')),
    source                   VARCHAR(20)   NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'AI', 'IMPORT')),

    primary_receipt_id       UUID          REFERENCES receipts(id) ON DELETE SET NULL,
    extraction_suggestion_id UUID,

    created_by_user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by_user_id      UUID          REFERENCES users(id) ON DELETE RESTRICT,
    approved_at              TIMESTAMPTZ,
    voided_by_user_id        UUID          REFERENCES users(id) ON DELETE RESTRICT,
    voided_at                TIMESTAMPTZ,
    void_reason              TEXT,

    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT chk_expenses_approved CHECK (
        (status = 'APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'APPROVED'
    ),
    CONSTRAINT chk_expenses_void CHECK (
        (status = 'VOID' AND voided_by_user_id IS NOT NULL AND voided_at IS NOT NULL)
        OR status <> 'VOID'
    )
);

CREATE INDEX idx_expenses_client_date   ON expenses(client_id, transaction_date);
CREATE INDEX idx_expenses_client_status ON expenses(client_id, status);
CREATE INDEX idx_expenses_firm_id       ON expenses(firm_id);
CREATE INDEX idx_expenses_category_id   ON expenses(category_id);
CREATE INDEX idx_expenses_vendor        ON expenses(client_id, vendor_name);
CREATE INDEX idx_expenses_approved      ON expenses(client_id, transaction_date)
    WHERE status = 'APPROVED';
CREATE INDEX idx_expenses_source        ON expenses(source);

CREATE TABLE income (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    firm_id                  UUID          NOT NULL REFERENCES firms(id) ON DELETE RESTRICT,
    client_id                UUID          NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    category_id              UUID          NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,

    transaction_date         DATE          NOT NULL,
    amount                   NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_code            CHAR(3)       NOT NULL DEFAULT 'LKR',
    customer_name            VARCHAR(200)  NOT NULL,
    description              TEXT,
    tax_amount               NUMERIC(19,4) CHECK (tax_amount IS NULL OR tax_amount >= 0),
    reference_no             VARCHAR(100),

    status                   VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'VOID')),
    source                   VARCHAR(20)   NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'AI', 'IMPORT')),

    primary_receipt_id       UUID          REFERENCES receipts(id) ON DELETE SET NULL,
    extraction_suggestion_id UUID,

    created_by_user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by_user_id      UUID          REFERENCES users(id) ON DELETE RESTRICT,
    approved_at              TIMESTAMPTZ,
    voided_by_user_id        UUID          REFERENCES users(id) ON DELETE RESTRICT,
    voided_at                TIMESTAMPTZ,
    void_reason              TEXT,

    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT chk_income_approved CHECK (
        (status = 'APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'APPROVED'
    ),
    CONSTRAINT chk_income_void CHECK (
        (status = 'VOID' AND voided_by_user_id IS NOT NULL AND voided_at IS NOT NULL)
        OR status <> 'VOID'
    )
);

CREATE INDEX idx_income_client_date   ON income(client_id, transaction_date);
CREATE INDEX idx_income_client_status ON income(client_id, status);
CREATE INDEX idx_income_firm_id       ON income(firm_id);
CREATE INDEX idx_income_category_id   ON income(category_id);
CREATE INDEX idx_income_customer      ON income(client_id, customer_name);
CREATE INDEX idx_income_approved      ON income(client_id, transaction_date)
    WHERE status = 'APPROVED';
CREATE INDEX idx_income_source        ON income(source);

CREATE TABLE expense_receipts (
    expense_id  UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    receipt_id  UUID NOT NULL REFERENCES receipts(id) ON DELETE RESTRICT,

    PRIMARY KEY (expense_id, receipt_id)
);

CREATE INDEX idx_expense_receipts_receipt ON expense_receipts(receipt_id);

CREATE TABLE income_receipts (
    income_id   UUID NOT NULL REFERENCES income(id) ON DELETE CASCADE,
    receipt_id  UUID NOT NULL REFERENCES receipts(id) ON DELETE RESTRICT,

    PRIMARY KEY (income_id, receipt_id)
);

CREATE INDEX idx_income_receipts_receipt ON income_receipts(receipt_id);
