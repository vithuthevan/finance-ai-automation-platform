package com.finance.platform.finance.application.dto;

import java.math.BigDecimal;

public record ReconciliationSummaryResponse(
		long totalTransactions,
		long matched,
		long suggested,
		long unmatched,
		long ignored,
		long pendingApproval,
		BigDecimal totalDebits,
		BigDecimal totalCredits,
		BigDecimal matchedValue,
		BigDecimal unmatchedValue,
		int reconciliationPercent
) {
}
