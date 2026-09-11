-- Align receipts.checksum_sha256 with JPA String mapping (varchar) used by Hibernate validate.

ALTER TABLE receipts
    ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);
