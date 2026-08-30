package com.finance.platform.reporting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReportingFacade {

	PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to);

	PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to);

	DashboardSummary generateDashboard(UUID clientId, LocalDate from, LocalDate to);

	record CategoryAmount(UUID categoryId, String categoryCode, String categoryName, BigDecimal amount) {
	}

	record PlSummary(
			UUID clientId,
			LocalDate from,
			LocalDate to,
			BigDecimal totalIncome,
			BigDecimal totalExpenses,
			BigDecimal netResult,
			List<CategoryAmount> incomeByCategory,
			List<CategoryAmount> expensesByCategory
	) {
	}

	record PeriodTotals(LocalDate from, LocalDate to, BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal netResult) {
	}

	record PlComparison(
			UUID clientId,
			PeriodTotals currentPeriod,
			PeriodTotals previousPeriod,
			BigDecimal incomeDifference,
			BigDecimal expenseDifference,
			BigDecimal netDifference,
			BigDecimal incomeChangePercent,
			BigDecimal expenseChangePercent,
			BigDecimal netChangePercent
	) {
	}

	record DashboardSummary(
			UUID clientId,
			BigDecimal income,
			BigDecimal expenses,
			BigDecimal profitOrLoss,
			long unreviewedDocuments,
			long unapprovedTransactions,
			long unreconciledBankEntries,
			int closeReadinessPercent,
			List<String> closeBlockers
	) {
	}
}
