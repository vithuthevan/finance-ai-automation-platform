package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
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
		AiExtractionMetadata ai = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		String type = resolveType(request != null ? request.transactionType() : null, ai);
		UUID categoryId = request != null && request.categoryId() != null
				? request.categoryId()
				: (receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getId() : null);
		if (categoryId == null) {
			throw new ValidationException("categoryId", "A category is required to accept the suggestion");
		}
		LocalDate date = ai.getSuggestedDate() != null ? ai.getSuggestedDate() : LocalDate.now();
		BigDecimal amount = ai.getSuggestedAmount();
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ValidationException("amount", "A positive suggested amount is required");
		}
		String party = ai.getSuggestedVendorOrCustomer() != null ? ai.getSuggestedVendorOrCustomer() : "Unknown";
		createDraft(client, receipt, type, categoryId, date, amount, ai.getSuggestedCurrency(), party,
				ai.getSuggestedDescription(), ai.getSuggestedTaxAmount(), ai.getSuggestedInvoiceNo(),
				parsePaymentMethod(ai.getSuggestedPaymentMethod()), TransactionSource.AI);
		return documentService.get(clientId, documentId);
	}

	@Transactional
	public DocumentResponse modify(UUID clientId, UUID documentId, ModifySuggestionRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		createDraft(client, receipt, request.transactionType(), request.categoryId(), request.transactionDate(),
				request.amount(), request.currencyCode(), request.partyName(), request.description(),
				request.taxAmount(), request.referenceNo(), request.paymentMethod(), TransactionSource.AI);
		return documentService.get(clientId, documentId);
	}

	@Transactional
	public DocumentResponse createFromDocument(UUID clientId, UUID documentId, ModifySuggestionRequest request) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = documentService.requireDocument(clientId, documentId);
		if (receipt.getStatus() == Receipt.ReceiptStatus.REJECTED) {
			throw new ValidationException("documentId", "Rejected documents cannot be converted into transactions");
		}
		createDraft(client, receipt, request.transactionType(), request.categoryId(), request.transactionDate(),
				request.amount(), request.currencyCode(), request.partyName(), request.description(),
				request.taxAmount(), request.referenceNo(), request.paymentMethod(), TransactionSource.MANUAL);
		receipt.setReviewedBy(clientAccessService.requireCurrentUserEntity());
		receipt.setReviewedAt(java.time.Instant.now());
		receipt.setReviewNote("Created " + request.transactionType().toUpperCase() + " from document");
		receiptRepository.save(receipt);
		return documentService.get(clientId, documentId);
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
			TransactionSource source
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
		receiptRepository.save(receipt);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.DOCUMENT_REVIEWED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(client.getId())
				.afterState(java.util.Map.of("transactionType", type.toUpperCase(), "status", "DRAFT"))
				.build());
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
