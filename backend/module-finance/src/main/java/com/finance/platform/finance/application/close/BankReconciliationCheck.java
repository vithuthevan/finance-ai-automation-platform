package com.finance.platform.finance.application.close;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hook for Phase 6/7 bank statement import and reconciliation.
 * Intentionally DISABLED so unmatched bank lines never block month-end close in this phase.
 */
@Component
@Order(90)
public class BankReconciliationCheck implements CloseCheck {

	@Override
	public String code() {
		return "BANK_RECONCILIATION_COMPLETE";
	}

	@Override
	public String label() {
		return "Bank reconciliation is complete";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.DISABLED;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		return List.of();
	}
}
