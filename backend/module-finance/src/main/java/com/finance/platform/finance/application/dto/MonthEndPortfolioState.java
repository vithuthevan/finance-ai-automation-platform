package com.finance.platform.finance.application.dto;

/**
 * Firm portfolio month-end readiness bucket for a client and period.
 */
public enum MonthEndPortfolioState {
	READY,
	ATTENTION,
	BLOCKED,
	CLOSED
}
