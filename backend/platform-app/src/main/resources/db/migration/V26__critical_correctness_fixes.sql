-- Critical fixes: bank row hash uniqueness, receipt optimistic versioning.

-- Normalize oversized external_row_hash values (legacy accountId:hex form) to the trailing 64 hex chars.
UPDATE bank_transactions
SET external_row_hash = RIGHT(external_row_hash, 64)
WHERE external_row_hash IS NOT NULL
  AND LENGTH(external_row_hash) > 64;

-- Drop non-unique index if present, then enforce per-account uniqueness.
DROP INDEX IF EXISTS idx_bank_txn_row_hash;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_txn_account_row_hash
    ON bank_transactions (bank_account_id, external_row_hash)
    WHERE external_row_hash IS NOT NULL;

ALTER TABLE receipts ADD COLUMN IF NOT EXISTS row_version INTEGER NOT NULL DEFAULT 0;
