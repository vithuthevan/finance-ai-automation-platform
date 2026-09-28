-- Slice 7: retain historical bank references on match-group items while enforcing
-- at-most-one *active* bank claim (rematch after invoice-payment reversal).
-- V38 all-time uniqueness blocked legitimate rematch when historical rows kept bank_transaction_id.

ALTER TABLE reconciliation_match_group_items
    ADD COLUMN IF NOT EXISTS bank_claim_active BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE reconciliation_match_group_items i
SET bank_claim_active = TRUE
FROM reconciliation_match_groups g
WHERE i.group_id = g.id
  AND i.bank_transaction_id IS NOT NULL
  AND g.status = 'CONFIRMED';

DROP INDEX IF EXISTS uq_recon_group_item_bank_txn;

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_group_item_bank_txn_active
    ON reconciliation_match_group_items (bank_transaction_id)
    WHERE bank_transaction_id IS NOT NULL AND bank_claim_active = TRUE;
