package com.finance.platform.ai.api;

import java.util.UUID;

public interface AiExtractionFacade {

	UUID triggerExtraction(UUID receiptId);

	ExtractionJobStatus getJobStatus(UUID jobId);

	enum ExtractionJobStatus {
		PENDING, PROCESSING, COMPLETED, FAILED
	}
}
