package com.finance.platform.finance.application.banking;

/**
 * Bank reconciliation progress: share of imported lines that are resolved (MATCHED or IGNORED).
 */
public final class ReconciliationProgressPercent {

	private ReconciliationProgressPercent() {
	}

	/**
	 * @param totalTransactions all bank lines in scope (any status)
	 * @param matched           lines in MATCHED status
	 * @param ignored           lines in IGNORED status
	 * @return 0–100 inclusive; 100 when there are no lines (nothing left to reconcile)
	 */
	public static int compute(long totalTransactions, long matched, long ignored) {
		if (totalTransactions <= 0) {
			return 100;
		}
		long resolved = matched + ignored;
		return (int) Math.round(resolved * 100.0 / totalTransactions);
	}
}
