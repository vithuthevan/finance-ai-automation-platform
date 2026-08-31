package com.finance.platform.ai.application;

import com.finance.platform.ai.config.AiProperties;
import com.finance.platform.ai.provider.MockExtractionProvider;
import com.finance.platform.ai.provider.OpenAiCompatibleExtractionProvider;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.storage.FileStorageService;
import com.finance.platform.finance.application.service.CategorySuggestionService;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAiProcessor {

	private static final Set<Receipt.DocumentType> EXTRACTABLE = Set.of(
			Receipt.DocumentType.RECEIPT,
			Receipt.DocumentType.INVOICE,
			Receipt.DocumentType.PURCHASE_INVOICE,
			Receipt.DocumentType.SALES_INVOICE,
			Receipt.DocumentType.CREDIT_NOTE
	);

	private final AiProperties aiProperties;
	private final OpenAiCompatibleExtractionProvider openAiProvider;
	private final MockExtractionProvider mockExtractionProvider;
	private final AccountingSuggestionService accountingSuggestionService;
	private final DocumentAiPersistenceService persistenceService;
	private final ReceiptJpaRepository receiptRepository;
	private final FirmJpaRepository firmRepository;
	private final CategoryJpaRepository categoryRepository;
	private final FileStorageService fileStorageService;
	private final CategorySuggestionService categorySuggestionService;

	public void process(UUID receiptId) {
		Receipt loaded = receiptRepository.findDetailedById(receiptId).orElse(null);
		if (loaded == null || loaded.getDeletedAt() != null) {
			return;
		}
		if (loaded.getStatus() == Receipt.ReceiptStatus.LINKED || loaded.getStatus() == Receipt.ReceiptStatus.REJECTED) {
			return;
		}
		int attemptNo = persistenceService.markProcessing(receiptId);
		if (attemptNo < 0) {
			return;
		}
		Instant started = Instant.now();
		log.info("processing started documentId={} attempt={}", receiptId, attemptNo);
		try {
			Receipt receipt = receiptRepository.findDetailedById(receiptId).orElseThrow();
			if (!EXTRACTABLE.contains(receipt.getDocumentType())) {
				persistenceService.completeSkipped(receiptId, attemptNo, started, ErrorCodes.EXTRACTION_NOT_AVAILABLE,
						"Automatic extraction is not used for this document type");
				return;
			}
			if (!firmAiEnabled(receipt.getFirmId()) || !aiProperties.isExtractionEnabled()) {
				persistenceService.completeDisabled(receiptId, attemptNo, started);
				return;
			}
			DocumentExtractionService extractor = aiProperties.isMockExtraction() ? mockExtractionProvider : openAiProvider;
			byte[] content = readAll(fileStorageService.open(receipt.getStorageKey()));
			Optional<ExtractedDocument> extracted = extractor instanceof OpenAiCompatibleExtractionProvider openAi
					? openAi.extract(content, receipt.getFileName(), receipt.getMimeType(),
					receipt.getDocumentType() != null ? receipt.getDocumentType().name() : null)
					: extractor.extract(content, receipt.getFileName(), receipt.getMimeType());
			if (extracted.isEmpty() || isHardFailure(extracted.get())) {
				String code = extracted.map(ExtractedDocument::failureCode).orElse(ErrorCodes.DOCUMENT_PROCESSING_FAILED);
				persistenceService.fail(receiptId, attemptNo, started, code,
						"Automatic extraction failed. Enter the transaction manually.");
				return;
			}
			ExtractedDocument facts = extracted.get();
			log.info("OCR completed documentId={} provider={}", receiptId, aiProperties.effectiveExtractionProvider());
			UUID clientId = receipt.getClient() != null ? receipt.getClient().getId() : null;
			List<Category> categories = categoryRepository.findAllByFirmIdAndDeletedAtIsNull(receipt.getFirmId()).stream()
					.filter(Category::isActive)
					.filter(category -> category.getClient() == null || category.getClient().getId().equals(clientId))
					.toList();
			Category historical = historicalCategory(receipt.getFirmId(), clientId, facts);
			AccountingSuggestion suggestion = accountingSuggestionService.suggest(facts, categories, historical);
			persistenceService.applySuccess(receiptId, facts, suggestion, attemptNo, started);
			log.info("suggestion completed documentId={} type={} category={}",
					receiptId, suggestion.transactionType(), suggestion.suggestedCategoryCode());
		} catch (Exception ex) {
			log.warn("processing failed documentId={} reason={}", receiptId, ex.getMessage());
			persistenceService.fail(receiptId, attemptNo, started, ErrorCodes.DOCUMENT_PROCESSING_FAILED,
					"Automatic extraction failed. Enter the transaction manually.");
		}
	}

	private static boolean isHardFailure(ExtractedDocument extracted) {
		return extracted.failureCode() != null
				&& extracted.totalAmount() == null
				&& extracted.partyName() == null
				&& extracted.documentDate() == null;
	}

	private Category historicalCategory(UUID firmId, UUID clientId, ExtractedDocument facts) {
		if (facts.suggestedTransactionType() != null && facts.suggestedTransactionType().equalsIgnoreCase("INCOME")) {
			return categorySuggestionService.suggestForCustomer(firmId, clientId, facts.partyName()).orElse(null);
		}
		return categorySuggestionService.suggestForVendor(firmId, clientId, facts.partyName()).orElse(null);
	}

	private boolean firmAiEnabled(UUID firmId) {
		return firmRepository.findById(firmId).map(Firm::isAiEnabled).orElse(true);
	}

	private static byte[] readAll(InputStream inputStream) throws Exception {
		try (inputStream) {
			return inputStream.readAllBytes();
		}
	}
}
