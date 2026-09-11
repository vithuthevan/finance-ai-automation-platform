-- Align platform_admin_grants with BaseEntity audit fields used by Hibernate validate.

ALTER TABLE platform_admin_grants
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;
