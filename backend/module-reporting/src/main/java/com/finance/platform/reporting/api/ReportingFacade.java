package com.finance.platform.reporting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReportingFacade {

	PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to);

	PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to);

	PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo);

	DashboardSummary generateDashboard(UUID clientId, LocalDate from, LocalDate to);

	IncomeSummary generateIncomeSummary(UUID clientId, LocalDate from, LocalDate to);

	ExpenseSummary generateExpenseSummary(UUID clientId, LocalDate from, LocalDate to);

	CashMovementSummary generateCashMovement(UUID clientId, LocalDate from, LocalDate to);

	List<MonthlyTrend> generateMonthlyTrend(UUID clientId, LocalDate from, LocalDate to);

	TopCategories generateTopCategories(UUID clientId, LocalDate from, LocalDate to);

	TransactionStatusSummary generateStatusSummary(UUID clientId);

	DocumentSupportSummary generateDocumentSupport(UUID clientId, LocalDate from, LocalDate to);

	PracticeDashboard generatePracticeDashboard();

	record CategoryAmount(
			UUID categoryId,
			String categoryCode,
			String categoryName,
			BigDecimal amount,
			BigDecimal percentageOfTotal,
			UUID parentId,
			String parentName
	) {
	}

	record NamedAmount(String name, BigDecimal amount, long count, BigDecimal percentageOfTotal) {
	}

	record PlSummary(
			UUID clientId,
			String clientName,
			LocalDate from,
			LocalDate to,
			String currencyCode,
			boolean hasApprovedData,
			String resultType,
			BigDecimal totalIncome,
			BigDecimal totalExpenses,
			BigDecimal netResult,
			List<CategoryAmount> incomeByCategory,
			List<CategoryAmount> expensesByCategory
	) {
	}

	record PeriodTotals(
			LocalDate from,
			LocalDate to,
			BigDecimal totalIncome,
			BigDecimal totalExpenses,
			BigDecimal netResult,
			String resultType
	) {
	}

	record PlComparison(
			UUID clientId,
			String clientName,
			String currencyCode,
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
			String clientName,
			String currencyCode,
			boolean hasApprovedData,
			BigDecimal income,
			BigDecimal expenses,
			BigDecimal profitOrLoss,
			String resultType,
			long unreviewedDocuments,
			long unapprovedTransactions,
			long unreconciledBankEntries,
			int closeReadinessPercent,
			List<String> closeBlockers,
			long unlinkedDocuments,
			long approvedWithDocuments,
			long approvedWithoutDocuments,
			TransactionStatusSummary statusSummary
	) {
	}

	record IncomeSummary(
			UUID clientId,
			String clientName,
			LocalDate from,
			LocalDate to,
			String currencyCode,
			boolean hasApprovedData,
			BigDecimal totalIncome,
			long transactionCount,
			BigDecimal averageAmount,
			List<CategoryAmount> byCategory,
			List<NamedAmount> byPaymentMethod
	) {
	}

	record ExpenseSummary(
			UUID clientId,
			String clientName,
			LocalDate from,
			LocalDate to,
			String currencyCode,
			boolean hasApprovedData,
			BigDecimal totalExpenses,
			long transactionCount,
			BigDecimal averageAmount,
			List<CategoryAmount> byCategory,
			List<LargestItem> largestExpenses
	) {
	}

	record LargestItem(UUID id, LocalDate transactionDate, BigDecimal amount, String partyName, String categoryName) {
	}

	record CashMovementSummary(
			UUID clientId,
			String clientName,
			LocalDate from,
			LocalDate to,
			String currencyCode,
			boolean hasApprovedData,
			BigDecimal approvedIncome,
			BigDecimal approvedExpenses,
			BigDecimal netRecordedMovement,
			String disclaimer
	) {
	}

	record MonthlyTrend(LocalDate monthStart, BigDecimal income, BigDecimal expenses, BigDecimal profitOrLoss) {
	}

	record TopCategories(List<CategoryAmount> largestIncomeCategories, List<CategoryAmount> largestExpenseCategories) {
	}

	record TransactionStatusSummary(
			long draftExpenses,
			long approvedExpenses,
			long voidExpenses,
			long draftIncome,
			long approvedIncome,
			long voidIncome
	) {
	}

	record DocumentSupportSummary(
			long approvedWithDocuments,
			long approvedWithoutDocuments,
			long unlinkedDocuments,
			long documentsAwaitingReview
	) {
	}

	record PracticeDashboard(
			long activeClients,
			long clientsWithDrafts,
			long documentsNeedingReview,
			long pendingApprovals,
			long clientsWithRecentActivity
	) {
	}
}
