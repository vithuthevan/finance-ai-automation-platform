package com.finance.platform.finance.application.dto;

import java.math.BigDecimal;

public record PeriodLedgerSummaryResponse(
		long draftExpenses,
		long approvedExpenses,
		long voidExpenses,
		BigDecimal draftExpenseAmount,
		BigDecimal approvedExpenseAmount,
		long draftIncome,
		long approvedIncome,
		long voidIncome,
		BigDecimal draftIncomeAmount,
		BigDecimal approvedIncomeAmount,
		BigDecimal netApproved
) {
}
