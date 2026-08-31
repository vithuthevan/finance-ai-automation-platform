package com.finance.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.platform.ai.config.AiProperties;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.DocumentProcessingAttempt;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.DocumentProcessingAttemptJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentAiPersistenceService {

	private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.05");

	private final ReceiptJpaRepository receiptRepository;
	private final CategoryJpaRepository categoryRepository;
	private final DocumentProcessingAttemptJpaRepository attemptRepository;
	private final AiProperties aiProperties;
	private final AuditLogger auditLogger;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int markProcessing(UUID receiptId) {
		Receipt receipt = receiptRepository.findById(receiptId).orElse(null);
		if (receipt == null) {
			return -1;
		}
		AiExtractionMetadata metadata = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		int next = (metadata.getProcessingAttemptCount() == null ? 0 : metadata.getProcessingAttemptCount()) + 1;
		receipt.setStatus(Receipt.ReceiptStatus.PROCESSING);
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.PROCESSING);
		metadata.setFailureCode(null);
		metadata.setFailureMessage(null);
		metadata.setProcessingAttemptCount(next);
		receipt.setAiMetadata(metadata);
		receiptRepository.save(receipt);
		audit(receipt, AuditAction.DOCUMENT_PROCESSING_STARTED, Map.of("attempt", next));
		return next;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void applySuccess(UUID receiptId, ExtractedDocument facts, AccountingSuggestion suggestion, int attemptNo, Instant started) {
		Receipt current = receiptRepository.findById(receiptId).orElseThrow();
		AiExtractionMetadata metadata = current.getAiMetadata() != null ? current.getAiMetadata() : new AiExtractionMetadata();
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.COMPLETED);
		metadata.setProcessedAt(Instant.now());
		metadata.setModelVersion(aiProperties.getOpenai().getModel());
		metadata.setPromptTemplateId(ExtractionPromptBuilder.TEMPLATE_ID);
		metadata.setAiProvider(aiProperties.effectiveExtractionProvider());
		metadata.setSuggestedVendorOrCustomer(facts.partyName());
		metadata.setSuggestedDate(facts.documentDate());
		metadata.setSuggestedAmount(facts.totalAmount());
		metadata.setSuggestedSubtotal(facts.subtotal());
		metadata.setSuggestedInvoiceNo(facts.invoiceNumber() != null ? facts.invoiceNumber() : facts.receiptNumber());
		metadata.setSuggestedDueDate(facts.dueDate());
		metadata.setSuggestedCurrency(facts.currency());
		metadata.setSuggestedTaxAmount(facts.taxAmount());
		metadata.setSuggestedPaymentMethod(sanitizePayment(facts.paymentMethod()));
		metadata.setSuggestedDescription(firstNonBlank(suggestion.suggestedDescription(), facts.description()));
		metadata.setConfidenceScore(firstDecimal(suggestion.confidence(), facts.overallConfidence()));
		metadata.setSupplierConfidence(facts.supplierConfidence());
		metadata.setDateConfidence(facts.dateConfidence());
		metadata.setAmountConfidence(facts.amountConfidence());
		metadata.setTaxConfidence(facts.taxConfidence());
		metadata.setOcrText(facts.ocrText());
		metadata.setRawExtractionJson(facts.rawJson());
		metadata.setInputTokens(facts.inputTokens());
		metadata.setOutputTokens(facts.outputTokens());
		metadata.setAmountInconsistency(amountInconsistent(facts));
		metadata.setDateWarning(dateWarning(facts.documentDate()));
		metadata.setFailureCode(null);
		metadata.setFailureMessage(null);
		metadata.setSuggestedType(parseType(suggestion.transactionType()));
		if (facts.lineItems() != null && !facts.lineItems().isEmpty()) {
			try {
				metadata.setLineItemsJson(objectMapper.writeValueAsString(facts.lineItems()));
			} catch (Exception ignored) {
				metadata.setLineItemsJson(null);
			}
		}
		if (suggestion.suggestedCategoryId() != null) {
			categoryRepository.findById(suggestion.suggestedCategoryId()).ifPresent(current::setSuggestedCategory);
		} else {
			current.setSuggestedCategory(null);
		}
		current.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
		current.setAiMetadata(metadata);
		receiptRepository.save(current);
		saveAttempt(current, attemptNo, started, "SUCCESS", null, null, facts.inputTokens(), facts.outputTokens());
		audit(current, AuditAction.DOCUMENT_EXTRACTION_COMPLETED, Map.of(
				"provider", String.valueOf(aiProperties.effectiveExtractionProvider()),
				"durationMs", Duration.between(started, Instant.now()).toMillis()
		));
		audit(current, AuditAction.AI_SUGGESTION_CREATED, Map.of(
				"transactionType", String.valueOf(suggestion.transactionType()),
				"source", String.valueOf(suggestion.source())
		));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(UUID receiptId, int attemptNo, Instant started, String code, String message) {
		Receipt current = receiptRepository.findById(receiptId).orElseThrow();
		AiExtractionMetadata metadata = current.getAiMetadata() != null ? current.getAiMetadata() : new AiExtractionMetadata();
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.FAILED);
		metadata.setFailureCode(code);
		metadata.setFailureMessage(safeMessage(message));
		metadata.setProcessedAt(Instant.now());
		metadata.setAiProvider(aiProperties.effectiveExtractionProvider());
		current.setStatus(Receipt.ReceiptStatus.FAILED);
		current.setAiMetadata(metadata);
		receiptRepository.save(current);
		saveAttempt(current, attemptNo, started, "FAILED", code, safeMessage(message), null, null);
		audit(current, AuditAction.DOCUMENT_EXTRACTION_FAILED, Map.of("failureCode", code));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeDisabled(UUID receiptId, int attemptNo, Instant started) {
		Receipt current = receiptRepository.findById(receiptId).orElseThrow();
		AiExtractionMetadata metadata = current.getAiMetadata() != null ? current.getAiMetadata() : new AiExtractionMetadata();
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.AI_DISABLED);
		metadata.setProcessedAt(Instant.now());
		metadata.setFailureCode(ErrorCodes.AI_DISABLED);
		metadata.setFailureMessage("AI extraction is disabled. Enter the transaction manually.");
		current.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
		current.setAiMetadata(metadata);
		receiptRepository.save(current);
		saveAttempt(current, attemptNo, started, "DISABLED", ErrorCodes.AI_DISABLED, metadata.getFailureMessage(), null, null);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeSkipped(UUID receiptId, int attemptNo, Instant started, String code, String message) {
		Receipt current = receiptRepository.findById(receiptId).orElseThrow();
		AiExtractionMetadata metadata = current.getAiMetadata() != null ? current.getAiMetadata() : new AiExtractionMetadata();
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.AI_DISABLED);
		metadata.setFailureCode(code);
		metadata.setFailureMessage(message);
		metadata.setProcessedAt(Instant.now());
		current.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
		current.setAiMetadata(metadata);
		receiptRepository.save(current);
		saveAttempt(current, attemptNo, started, "SKIPPED", code, message, null, null);
	}

	private void saveAttempt(
			Receipt receipt,
			int attemptNo,
			Instant started,
			String status,
			String failureCode,
			String failureMessage,
			Integer inputTokens,
			Integer outputTokens
	) {
		DocumentProcessingAttempt attempt = DocumentProcessingAttempt.builder()
				.receipt(receipt)
				.attemptNo(attemptNo)
				.provider(aiProperties.effectiveExtractionProvider())
				.modelVersion(aiProperties.getOpenai().getModel())
				.status(status)
				.failureCode(failureCode)
				.failureMessage(failureMessage)
				.durationMs((int) Duration.between(started, Instant.now()).toMillis())
				.inputTokens(inputTokens)
				.outputTokens(outputTokens)
				.build();
		attempt.setFirmId(receipt.getFirmId());
		attemptRepository.save(attempt);
	}

	private void audit(Receipt receipt, AuditAction action, Map<String, Object> after) {
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(receipt.getFirmId())
				.action(action)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(receipt.getClient() != null ? receipt.getClient().getId() : null)
				.afterState(after)
				.build());
	}

	private static boolean amountInconsistent(ExtractedDocument facts) {
		if (facts.subtotal() == null || facts.taxAmount() == null || facts.totalAmount() == null) {
			return false;
		}
		BigDecimal expected = facts.subtotal().add(facts.taxAmount());
		if (facts.discountAmount() != null) {
			expected = expected.subtract(facts.discountAmount());
		}
		return expected.subtract(facts.totalAmount()).abs().compareTo(AMOUNT_TOLERANCE) > 0;
	}

	private static String dateWarning(LocalDate date) {
		if (date == null) {
			return null;
		}
		LocalDate today = LocalDate.now();
		if (date.isAfter(today.plusDays(7))) {
			return "FUTURE_DATE";
		}
		if (date.isBefore(today.minusYears(5))) {
			return "VERY_OLD_DATE";
		}
		return null;
	}

	private static AiExtractionMetadata.SuggestedTransactionType parseType(String raw) {
		if (raw == null) {
			return AiExtractionMetadata.SuggestedTransactionType.UNKNOWN;
		}
		try {
			return AiExtractionMetadata.SuggestedTransactionType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return AiExtractionMetadata.SuggestedTransactionType.UNKNOWN;
		}
	}

	private static String sanitizePayment(String raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.trim().toUpperCase().replace(' ', '_');
		if (value.equals("BANK_TRANSFER") || value.equals("CASH") || value.equals("CARD")
				|| value.equals("CHEQUE") || value.equals("ONLINE") || value.equals("OTHER")) {
			return value;
		}
		return null;
	}

	private static String safeMessage(String message) {
		if (message == null) {
			return "Processing failed";
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second;
	}

	private static BigDecimal firstDecimal(BigDecimal first, BigDecimal second) {
		return first != null ? first : second;
	}
}
