package com.finance.platform.ai.application;

import com.finance.platform.ai.config.AiProperties;
import com.finance.platform.ai.provider.DisabledAiProvider;
import com.finance.platform.ai.provider.OpenAiCompatibleExtractionProvider;
import com.finance.platform.core.storage.FileStorageService;
import com.finance.platform.finance.application.service.CategorySuggestionService;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAiProcessor {

	private final AiProperties aiProperties;
	private final DisabledAiProvider disabledAiProvider;
	private final OpenAiCompatibleExtractionProvider openAiProvider;
	private final ReceiptJpaRepository receiptRepository;
	private final FileStorageService fileStorageService;
	private final CategorySuggestionService categorySuggestionService;

	@Transactional
	public void process(UUID receiptId) {
		Receipt receipt = receiptRepository.findById(receiptId).orElse(null);
		if (receipt == null || receipt.getDeletedAt() != null) {
			return;
		}
		if (receipt.getStatus() == Receipt.ReceiptStatus.LINKED
				|| receipt.getStatus() == Receipt.ReceiptStatus.REJECTED) {
			return;
		}
		AiExtractionMetadata metadata = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		DocumentExtractionService extractor = aiProperties.isProviderConfigured() ? openAiProvider : disabledAiProvider;
		if (!extractor.isEnabled()) {
			metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.AI_DISABLED);
			metadata.setProcessedAt(Instant.now());
			if (receipt.getStatus() != Receipt.ReceiptStatus.LINKED
					&& receipt.getStatus() != Receipt.ReceiptStatus.REJECTED) {
				receipt.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
			}
			receipt.setAiMetadata(metadata);
			receiptRepository.save(receipt);
			return;
		}
		receipt.setStatus(Receipt.ReceiptStatus.PROCESSING);
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.PROCESSING);
		receipt.setAiMetadata(metadata);
		receiptRepository.save(receipt);

		try {
			byte[] content = readAll(fileStorageService.open(receipt.getStorageKey()));
			Optional<ExtractedDocument> extracted = extractor.extract(content, receipt.getFileName(), receipt.getMimeType());
			if (extracted.isEmpty()) {
				metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.FAILED);
				receipt.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
				receipt.setAiMetadata(metadata);
				receiptRepository.save(receipt);
				return;
			}
			applyExtraction(receipt, metadata, extracted.get());
			receipt.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
			receiptRepository.save(receipt);
		} catch (Exception ex) {
			log.warn("Document AI processing failed for {}: {}", receiptId, ex.getMessage());
			metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.FAILED);
			receipt.setStatus(Receipt.ReceiptStatus.FAILED);
			receipt.setAiMetadata(metadata);
			receiptRepository.save(receipt);
		}
	}

	private void applyExtraction(Receipt receipt, AiExtractionMetadata metadata, ExtractedDocument extracted) {
		metadata.setExtractionStatus(AiExtractionMetadata.ExtractionStatus.COMPLETED);
		metadata.setProcessedAt(Instant.now());
		metadata.setModelVersion(aiProperties.getOpenai().getModel());
		metadata.setSuggestedVendorOrCustomer(extracted.merchantOrCustomer());
		metadata.setSuggestedDate(extracted.date());
		metadata.setSuggestedAmount(extracted.totalAmount());
		metadata.setSuggestedInvoiceNo(extracted.invoiceNumber());
		metadata.setSuggestedDueDate(extracted.dueDate());
		metadata.setSuggestedCurrency(extracted.currency());
		metadata.setSuggestedTaxAmount(extracted.tax());
		metadata.setSuggestedPaymentMethod(extracted.paymentMethod());
		metadata.setSuggestedDescription(extracted.description());
		metadata.setConfidenceScore(extracted.confidence());
		metadata.setRawExtractionJson(extracted.rawJson());
		if (extracted.suggestedTransactionType() != null) {
			try {
				metadata.setSuggestedType(AiExtractionMetadata.SuggestedTransactionType.valueOf(
						extracted.suggestedTransactionType().trim().toUpperCase()));
			} catch (IllegalArgumentException ignored) {
				metadata.setSuggestedType(AiExtractionMetadata.SuggestedTransactionType.EXPENSE);
			}
		}
		UUID firmId = receipt.getFirmId();
		UUID clientId = receipt.getClient() != null ? receipt.getClient().getId() : null;
		Optional<Category> historical = metadata.getSuggestedType() == AiExtractionMetadata.SuggestedTransactionType.INCOME
				? categorySuggestionService.suggestForCustomer(firmId, clientId, extracted.merchantOrCustomer())
				: categorySuggestionService.suggestForVendor(firmId, clientId, extracted.merchantOrCustomer());
		historical.or(() -> categorySuggestionService.findByCode(firmId, clientId, extracted.candidateCategoryCode()))
				.ifPresent(receipt::setSuggestedCategory);
		receipt.setAiMetadata(metadata);
	}

	private static byte[] readAll(InputStream inputStream) throws Exception {
		try (inputStream) {
			return inputStream.readAllBytes();
		}
	}
}
