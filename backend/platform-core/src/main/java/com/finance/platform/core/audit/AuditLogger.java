package com.finance.platform.core.audit;

/**
 * Append-only business audit trail. Implementations must INSERT only —
 * never update or delete existing audit rows.
 */
public interface AuditLogger {

	/**
	 * Records an audit event in the caller's transaction (preferred for ledger writes).
	 */
	void record(AuditEvent event);

	/**
	 * Records an audit event in a new transaction so it survives caller rollback
	 * (e.g. LOGIN_FAILURE that still throws to the client).
	 */
	void recordIndependent(AuditEvent event);
}
