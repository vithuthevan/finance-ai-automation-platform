package com.finance.platform.finance.application.dto;

public record PeriodReadinessSummaryResponse(
		long draftExpenses,
		long draftIncome,
		long documentsNeedingReview,
		long failedUnlinkedDocuments,
		long unlinkedDocuments,
		long openDocumentRequests,
		long approvedTransactionsWithoutDocuments,
		long approvedTransactionsWithDocuments
) {
}
