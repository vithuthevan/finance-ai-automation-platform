package com.finance.platform.core.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Insert and query only — never expose delete or update operations for audit rows.
 */
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID> {
}
