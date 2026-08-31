package com.finance.platform.finance.application.close;

/**
 * Close-check severity. BLOCKER prevents close. WARNING and INFO do not.
 * DISABLED is reserved for future checks (for example bank reconciliation).
 */
public enum CloseCheckSeverity {
	BLOCKER,
	WARNING,
	INFO,
	DISABLED
}
