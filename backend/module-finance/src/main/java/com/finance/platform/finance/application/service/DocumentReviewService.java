package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.AcceptSuggestionRequest;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.dto.ModifySuggestionRequest;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Category;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.PaymentMethod;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.TransactionSource;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.CategoryJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentReviewService {

	private final DocumentService documentService;
	private final ClientAccessService clientAccessService;
	private final ReceiptJpaRepository receiptRepository;
	private final CategoryJpaRepository categoryRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final PeriodCloseService periodCloseService;
	private final AuditLogger auditLogger;

	@Transactional
	public DocumentResponse accept(UUID clientId, UUID documentId, AcceptSuggestionRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		assertCanCreateDraftFromDocument(receipt);
		AiExtractionMetadata ai = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		String type = resolveType(request != null ? request.transactionType() : null, ai);
		if ("UNKNOWN".equals(type)) {
			throw new ValidationException(ErrorCodes.INVALID_AI_SUGGESTION, "transactionType", "Transaction type must be EXPENSE or INCOME");
		}
		UUID categoryId = request != null && request.categoryId() != null
				? request.categoryId()
				: (receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getId() : null);
		if (categoryId == null) {
			throw new ValidationException("categoryId", "A category is required to accept the suggestion");
		}
		LocalDate date = request != null && request.transactionDate() != null
				? request.transactionDate()
				: (ai.getSuggestedDate() != null ? ai.getSuggestedDate() : LocalDate.now());
		BigDecimal amount = request != null && request.amount() != null ? request.amount() : ai.getSuggestedAmount();
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ValidationException("amount", "A positive amount is required");
		}
		String party = request != null && request.partyName() != null && !request.partyName().isBlank()
				? request.partyName()
				: (ai.getSuggestedVendorOrCustomer() != null ? ai.getSuggestedVendorOrCustomer() : "Unknown");
		boolean modified = isModified(request, receipt, ai, type, categoryId, date, amount, party);
		String outcome = modified
				? AiExtractionMetadata.ReviewOutcome.MODIFIED.name()
				: AiExtractionMetadata.ReviewOutcome.ACCEPTED.name();
		createDraft(client, receipt, type, categoryId, date, amount,
				request != null && request.currencyCode() != null ? request.currencyCode() : ai.getSuggestedCurrency(),
				party,
				request != null && request.description() != null ? request.description() : ai.getSuggestedDescription(),
				request != null && request.taxAmount() != null ? request.taxAmount() : ai.getSuggestedTaxAmount(),
				request != null && request.referenceNo() != null ? request.referenceNo() : ai.getSuggestedInvoiceNo(),
				request != null && request.paymentMethod() != null
						? request.paymentMethod()
						: parsePaymentMethod(ai.getSuggestedPaymentMethod()),
				TransactionSource.AI,
				outcome);
		return documentService.get(clientId, documentId);
	}

	@Transactional
	public DocumentResponse modify(UUID clientId, UUID documentId, ModifySuggestionRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		assertCanCreateDraftFromDocument(receipt);
		createDraft(client, receipt, request.transactionType(), request.categoryId(), request.transactionDate(),
				request.amount(), request.currencyCode(), request.partyName(), request.description(),
				request.taxAmount(), request.referenceNo(), request.paymentMethod(), TransactionSource.AI,
				AiExtractionMetadata.ReviewOutcome.MODIFIED.name());
		return documentService.get(clientId, documentId);
	}

	@Transactional
	public DocumentResponse createFromDocument(UUID clientId, UUID documentId, ModifySuggestionRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		assertCanCreateDraftFromDocument(receipt);
		createDraft(client, receipt, request.transactionType(), request.categoryId(), request.transactionDate(),
				request.amount(), request.currencyCode(), request.partyName(), request.description(),
				request.taxAmount(), request.referenceNo(), request.paymentMethod(), TransactionSource.MANUAL,
				AiExtractionMetadata.ReviewOutcome.MANUAL.name());
		return documentService.get(clientId, documentId);
	}

	@Transactional
	public DocumentResponse rejectSuggestion(UUID clientId, UUID documentId, String note) {
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		if (receipt.getStatus() == Receipt.ReceiptStatus.LINKED) {
			throw new ValidationException("documentId", "Suggestion was already accepted");
		}
		AiExtractionMetadata ai = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		ai.setReviewOutcome(AiExtractionMetadata.ReviewOutcome.REJECTED.name());
		receipt.setAiMetadata(ai);
		receipt.setReviewedBy(clientAccessService.requireCurrentUserEntity());
		receipt.setReviewedAt(java.time.Instant.now());
		receipt.setReviewNote(note != null && !note.isBlank() ? note : "AI suggestion rejected; document kept for manual entry");
		if (receipt.getStatus() == Receipt.ReceiptStatus.FAILED) {
			receipt.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
		}
		receiptRepository.save(receipt);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(receipt.getFirmId())
				.action(AuditAction.AI_SUGGESTION_REJECTED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(clientId)
				.afterState(java.util.Map.of("documentStatus", receipt.getStatus().name()))
				.build());
		return documentService.get(clientId, documentId);
	}

	private void assertCanCreateDraftFromDocument(Receipt receipt) {
		if (receipt.getStatus() == Receipt.ReceiptStatus.REJECTED || receipt.getStatus() == Receipt.ReceiptStatus.LINKED) {
			throw new ValidationException("documentId", "This document cannot be converted into a new transaction");
		}
	}

	private void createDraft(
			Client client,
			Receipt receipt,
			String type,
			UUID categoryId,
			LocalDate date,
			BigDecimal amount,
			String currency,
			String party,
			String description,
			BigDecimal taxAmount,
			String referenceNo,
			PaymentMethod paymentMethod,
			TransactionSource source,
			String reviewOutcome
	) {
		User currentUser = clientAccessService.requireCurrentUserEntity();
		periodCloseService.assertPeriodOpen(client.getId(), date);
		Category category = categoryRepository.findByIdAndFirmIdAndDeletedAtIsNull(categoryId, client.getFirmId())
				.orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
		if (!category.isActive()) {
			throw new ValidationException("categoryId", "Category is not active");
		}
		if ("INCOME".equalsIgnoreCase(type)) {
			if (category.getCategoryType() == Category.CategoryType.EXPENSE) {
				throw new ValidationException("categoryId", "Category is not valid for income");
			}
			Income income = Income.builder()
					.client(client)
					.category(category)
					.transactionDate(date)
					.amount(amount)
					.currencyCode(currency != null ? currency : "LKR")
					.customerName(party)
					.description(description)
					.taxAmount(taxAmount)
					.referenceNo(referenceNo)
					.paymentMethod(paymentMethod)
					.status(TransactionStatus.DRAFT)
					.source(source)
					.createdByUser(currentUser)
					.extractionSuggestionId(receipt.getId())
					.build();
			income.setFirmId(client.getFirmId());
			income.linkReceipt(receipt);
			incomeRepository.save(income);
		} else if ("EXPENSE".equalsIgnoreCase(type)) {
			if (category.getCategoryType() == Category.CategoryType.INCOME) {
				throw new ValidationException("categoryId", "Category is not valid for expenses");
			}
			Expense expense = Expense.builder()
					.client(client)
					.category(category)
					.transactionDate(date)
					.amount(amount)
					.currencyCode(currency != null ? currency : "LKR")
					.vendorName(party)
					.description(description)
					.taxAmount(taxAmount)
					.referenceNo(referenceNo)
					.status(TransactionStatus.DRAFT)
					.source(source)
					.createdByUser(currentUser)
					.extractionSuggestionId(receipt.getId())
					.build();
			expense.setFirmId(client.getFirmId());
			expense.linkReceipt(receipt);
			expenseRepository.save(expense);
		} else {
			throw new ValidationException("transactionType", "Transaction type must be EXPENSE or INCOME");
		}
		receipt.setStatus(Receipt.ReceiptStatus.LINKED);
		AiExtractionMetadata metadata = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		metadata.setReviewOutcome(reviewOutcome);
		receipt.setAiMetadata(metadata);
		receipt.setConfirmedCategory(category);
		receipt.setReviewedBy(currentUser);
		receipt.setReviewedAt(java.time.Instant.now());
		receipt.setReviewNote(source == TransactionSource.AI
				? (AiExtractionMetadata.ReviewOutcome.MODIFIED.name().equals(reviewOutcome)
				? "Accepted AI suggestion with modifications as DRAFT"
				: "Accepted AI suggestion as DRAFT")
				: "Created MANUAL draft from document");
		receiptRepository.save(receipt);
		AuditAction action = AiExtractionMetadata.ReviewOutcome.MODIFIED.name().equals(reviewOutcome)
				? AuditAction.AI_SUGGESTION_MODIFIED
				: (source == TransactionSource.AI ? AuditAction.AI_SUGGESTION_ACCEPTED : AuditAction.DOCUMENT_REVIEWED);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(action)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(client.getId())
				.afterState(java.util.Map.of(
						"transactionType", type.toUpperCase(),
						"status", "DRAFT",
						"source", source.name(),
						"reviewOutcome", reviewOutcome,
						"suggestedCategoryId", receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getId().toString() : "",
						"confirmedCategoryId", category.getId().toString()
				))
				.build());
	}

	private static boolean isModified(
			AcceptSuggestionRequest request,
			Receipt receipt,
			AiExtractionMetadata ai,
			String type,
			UUID categoryId,
			LocalDate date,
			BigDecimal amount,
			String party
	) {
		if (request == null) {
			return false;
		}
		UUID suggestedCategory = receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getId() : null;
		if (suggestedCategory != null && !suggestedCategory.equals(categoryId)) {
			return true;
		}
		if (ai.getSuggestedType() != null && !ai.getSuggestedType().name().equalsIgnoreCase(type)) {
			return true;
		}
		if (ai.getSuggestedDate() != null && !ai.getSuggestedDate().equals(date)) {
			return true;
		}
		if (ai.getSuggestedAmount() != null && ai.getSuggestedAmount().compareTo(amount) != 0) {
			return true;
		}
		return request.partyName() != null && ai.getSuggestedVendorOrCustomer() != null
				&& !request.partyName().equalsIgnoreCase(ai.getSuggestedVendorOrCustomer());
	}

	private String resolveType(String requested, AiExtractionMetadata ai) {
		if (requested != null && !requested.isBlank()) {
			return requested;
		}
		if (ai.getSuggestedType() != null) {
			return ai.getSuggestedType().name();
		}
		return "EXPENSE";
	}

	private PaymentMethod parsePaymentMethod(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return PaymentMethod.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
