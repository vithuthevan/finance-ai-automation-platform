package com.finance.platform.finance.application.dto;

public record PeriodDocumentSummaryResponse(
		long total,
		long linked,
		long unlinked,
		long needsReview,
		long rejected,
		long failed,
		long processed
) {
}
