package com.finance.platform.finance.application.dto;

public record PracticeTodayResponse(
		long clientsNeedAttention,
		long readyToClose,
		long blockedByMissingDocuments,
		long unreconciledTransactions,
		long aiReviewsPending,
		long overdueInvoices,
		long closeBlocked,
		long closeNeedsAttention
) {
}
