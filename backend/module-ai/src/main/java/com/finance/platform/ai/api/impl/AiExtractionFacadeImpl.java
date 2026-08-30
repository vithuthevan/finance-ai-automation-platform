package com.finance.platform.ai.api.impl;

import com.finance.platform.ai.api.AiExtractionFacade;
import com.finance.platform.ai.application.DocumentAiProcessor;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiExtractionFacadeImpl implements AiExtractionFacade {

	private final DocumentAiProcessor documentAiProcessor;
	private final ReceiptJpaRepository receiptRepository;

	@Override
	public UUID triggerExtraction(UUID receiptId) {
		documentAiProcessor.process(receiptId);
		return receiptId;
	}

	@Override
	public ExtractionJobStatus getJobStatus(UUID jobId) {
		Receipt receipt = receiptRepository.findById(jobId).orElse(null);
		if (receipt == null || receipt.getAiMetadata() == null || receipt.getAiMetadata().getExtractionStatus() == null) {
			return ExtractionJobStatus.PENDING;
		}
		AiExtractionMetadata.ExtractionStatus status = receipt.getAiMetadata().getExtractionStatus();
		return switch (status) {
			case PROCESSING, PENDING -> ExtractionJobStatus.PROCESSING;
			case COMPLETED, AI_DISABLED -> ExtractionJobStatus.COMPLETED;
			case FAILED -> ExtractionJobStatus.FAILED;
			case NOT_STARTED -> ExtractionJobStatus.PENDING;
		};
	}
}
