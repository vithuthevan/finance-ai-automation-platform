-- Phase 3: refresh the approved-transactions view for reporting.
-- DRAFT and VOID remain excluded from active totals.
-- Category metadata and evidence flags are exposed for read models.
-- DROP + CREATE: Postgres rejects CREATE OR REPLACE when column types/order change.

DROP VIEW IF EXISTS v_approved_transactions;

CREATE VIEW v_approved_transactions AS
SELECT
    e.id,
    e.firm_id,
    e.client_id,
    'EXPENSE'::varchar(20) AS transaction_type,
    e.transaction_date,
    e.amount,
    e.tax_amount,
    e.currency_code,
    e.vendor_name AS party_name,
    NULL::varchar(30) AS payment_method,
    e.category_id,
    c.code AS category_code,
    c.name AS category_name,
    c.parent_id AS category_parent_id,
    e.status,
    e.source,
    e.primary_receipt_id,
    (
        e.primary_receipt_id IS NOT NULL
        OR EXISTS (SELECT 1 FROM expense_receipts er WHERE er.expense_id = e.id)
    ) AS has_document,
    e.approved_at,
    e.created_at
FROM expenses e
LEFT JOIN categories c ON c.id = e.category_id
WHERE e.status = 'APPROVED'

UNION ALL

SELECT
    i.id,
    i.firm_id,
    i.client_id,
    'INCOME'::varchar(20) AS transaction_type,
    i.transaction_date,
    i.amount,
    i.tax_amount,
    i.currency_code,
    i.customer_name AS party_name,
    i.payment_method,
    i.category_id,
    c.code AS category_code,
    c.name AS category_name,
    c.parent_id AS category_parent_id,
    i.status,
    i.source,
    i.primary_receipt_id,
    (
        i.primary_receipt_id IS NOT NULL
        OR EXISTS (SELECT 1 FROM income_receipts ir WHERE ir.income_id = i.id)
    ) AS has_document,
    i.approved_at,
    i.created_at
FROM income i
LEFT JOIN categories c ON c.id = i.category_id
WHERE i.status = 'APPROVED';
