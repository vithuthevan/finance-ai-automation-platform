package com.finance.platform.finance.application.close;

import java.util.List;

/**
 * One close-readiness check. New period checks (for example bank reconciliation in a later phase)
 * should implement this interface rather than expanding {@code CloseReadinessService} into a monolith.
 * Default severity can later be made firm-configurable without changing controllers.
 */
public interface CloseCheck {

	String code();

	String label();

	CloseCheckSeverity defaultSeverity();

	default boolean enabled() {
		return defaultSeverity() != CloseCheckSeverity.DISABLED;
	}

	List<CloseFinding> evaluate(CloseCheckContext context);
}
