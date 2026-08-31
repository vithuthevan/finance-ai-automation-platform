package com.finance.platform.ai.api.impl;

import com.finance.platform.ai.api.AiExtractionFacade;
import com.finance.platform.ai.application.DocumentAiProcessor;
import com.finance.platform.ai.config.AiProperties;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.DocumentProcessingAttemptJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiExtractionFacadeImpl implements AiExtractionFacade {

	private final DocumentAiProcessor documentAiProcessor;
	private final ReceiptJpaRepository receiptRepository;
	private final DocumentProcessingAttemptJpaRepository attemptRepository;
	private final AiProperties aiProperties;

	@Override
	public UUID triggerExtraction(UUID receiptId) {
		documentAiProcessor.process(receiptId);
		return receiptId;
	}

	@Override
	public void assertRetryAllowed(UUID receiptId) {
		Receipt receipt = receiptRepository.findById(receiptId).orElse(null);
		if (receipt == null) {
			throw new BusinessException(ErrorCodes.DOCUMENT_NOT_FOUND, "Document not found");
		}
		if (receipt.getStatus() == Receipt.ReceiptStatus.PROCESSING
				&& receipt.getAiMetadata() != null
				&& receipt.getAiMetadata().getProcessedAt() != null
				&& receipt.getAiMetadata().getProcessedAt().isAfter(Instant.now().minusSeconds(90))) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_PROCESSING, "Document is already being processed");
		}
		if (receipt.getStatus() == Receipt.ReceiptStatus.PROCESSING
				&& receipt.getUpdatedAt() != null
				&& receipt.getUpdatedAt().isAfter(Instant.now().minusSeconds(90))) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_PROCESSING, "Document is already being processed");
		}
		long recent = attemptRepository.countByReceipt_IdAndCreatedAtAfter(receiptId, Instant.now().minus(1, ChronoUnit.DAYS));
		if (recent >= Math.max(1, aiProperties.getMaxRetriesPerDocument())) {
			throw new BusinessException(ErrorCodes.AI_RATE_LIMITED, "Too many processing retries for this document");
		}
	}

	@Override
	public void retryExtraction(UUID receiptId) {
		assertRetryAllowed(receiptId);
		documentAiProcessor.process(receiptId);
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

	@Override
	public AiUsageMetrics usageMetrics(UUID firmId) {
		long processed = attemptRepository.countByFirmIdAndStatus(firmId, "SUCCESS")
				+ attemptRepository.countByFirmIdAndStatus(firmId, "FAILED");
		long success = attemptRepository.countByFirmIdAndStatus(firmId, "SUCCESS");
		long failed = attemptRepository.countByFirmIdAndStatus(firmId, "FAILED");
		return new AiUsageMetrics(
				processed,
				success,
				failed,
				receiptRepository.countByFirmIdAndAiMetadata_ReviewOutcome(firmId, "ACCEPTED"),
				receiptRepository.countByFirmIdAndAiMetadata_ReviewOutcome(firmId, "MODIFIED"),
				receiptRepository.countByFirmIdAndAiMetadata_ReviewOutcome(firmId, "REJECTED"),
				attemptRepository.averageDurationMs(firmId)
		);
	}
}
