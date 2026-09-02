package com.finance.platform.finance.application.dto;

public record WorkSummaryResponse(
		long documentsToReview,
		long processingFailures,
		long pendingApprovals,
		long openDocumentRequests,
		long overdueDocumentRequests,
		long bankItemsUnresolved,
		long clientsReadyToClose
) {
}
