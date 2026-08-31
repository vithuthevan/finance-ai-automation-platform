package com.finance.platform.ai.api;

import java.util.UUID;

public interface AiExtractionFacade {

	UUID triggerExtraction(UUID receiptId);

	void assertRetryAllowed(UUID receiptId);

	void retryExtraction(UUID receiptId);

	ExtractionJobStatus getJobStatus(UUID jobId);

	AiUsageMetrics usageMetrics(UUID firmId);

	enum ExtractionJobStatus {
		PENDING, PROCESSING, COMPLETED, FAILED
	}

	record AiUsageMetrics(
			long documentsProcessed,
			long successes,
			long failures,
			long suggestionsAccepted,
			long suggestionsModified,
			long suggestionsRejected,
			double averageDurationMs
	) {
	}
}
