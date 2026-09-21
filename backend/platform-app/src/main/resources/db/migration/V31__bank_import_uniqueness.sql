-- Phase 1.5: bank import duplicate protection under concurrent uploads.

CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_import_account_checksum
    ON bank_imports (bank_account_id, checksum)
    WHERE bank_account_id IS NOT NULL AND checksum IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bank_txn_account_row_hash
    ON bank_transactions (bank_account_id, external_row_hash)
    WHERE bank_account_id IS NOT NULL AND external_row_hash IS NOT NULL;
