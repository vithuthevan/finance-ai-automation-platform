-- V6: add payment method to income for reconciliation and channel analytics

ALTER TABLE income
    ADD COLUMN payment_method VARCHAR(20)
        CHECK (payment_method IS NULL OR payment_method IN (
            'BANK_TRANSFER', 'CASH', 'CARD', 'CHEQUE', 'ONLINE', 'OTHER'
        ));

CREATE INDEX idx_income_payment_method ON income(client_id, payment_method);
