-- PostgreSQL UNIQUE (firm_id, client_id, code) does not enforce uniqueness
-- when client_id IS NULL. Replace with partial unique indexes.

ALTER TABLE categories DROP CONSTRAINT IF EXISTS uq_categories_scope_code;

CREATE UNIQUE INDEX uq_categories_firm_code
    ON categories (firm_id, code)
    WHERE client_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_categories_firm_client_code
    ON categories (firm_id, client_id, code)
    WHERE client_id IS NOT NULL AND deleted_at IS NULL;
