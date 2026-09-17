package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.event.DomainEventPublisher;
import com.finance.platform.core.subscription.SubscriptionQuotaGuard;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.DuplicateDocumentException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.storage.FileStorageService;
import com.finance.platform.core.storage.StorageProperties;
import com.finance.platform.finance.application.dto.DocumentResponse;
import com.finance.platform.finance.application.event.DocumentUploadedEvent;
import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.DocumentProcessingAttemptJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp", "csv");

	private final ReceiptJpaRepository receiptRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final ClientAccessService clientAccessService;
	private final PeriodCloseService periodCloseService;
	private final FileStorageService fileStorageService;
	private final StorageProperties storageProperties;
	private final DomainEventPublisher eventPublisher;
	private final AuditLogger auditLogger;
	private final DocumentProcessingAttemptJpaRepository attemptRepository;
	private final SubscriptionQuotaGuard subscriptionQuotaGuard;

	@Transactional
	public DocumentResponse upload(
			UUID clientId,
			byte[] content,
			String originalFilename,
			String declaredMimeType,
			Receipt.DocumentType documentType,
			String description,
			boolean allowDuplicate
	) {
		Client client = clientAccessService.requireUploadAccess(clientId);
		User uploader = clientAccessService.requireCurrentUserEntity();
		validateFile(content, originalFilename);
		assertUploadAllowed(client.getFirmId(), content.length);

		String checksum = sha256(content);
		Receipt existing = receiptRepository
				.findFirstByFirmIdAndClient_IdAndChecksumSha256AndDeletedAtIsNull(client.getFirmId(), clientId, checksum)
				.orElse(null);
		if (existing != null && !allowDuplicate) {
			throw new DuplicateDocumentException(existing.getId(), checksum);
		}

		String safeName = sanitizeFilename(originalFilename);
		String storageKey = "firms/" + client.getFirmId() + "/clients/" + client.getId()
				+ "/documents/" + UUID.randomUUID();
		String detectedMime = detectMimeType(content, declaredMimeType, safeName);

		fileStorageService.store(storageKey, content, detectedMime);
		try {
			AiExtractionMetadata metadata = AiExtractionMetadata.builder()
					.extractionStatus(AiExtractionMetadata.ExtractionStatus.NOT_STARTED)
					.processingAttemptCount(0)
					.build();
			Receipt receipt = Receipt.builder()
					.client(client)
					.uploadedBy(uploader)
					.fileName(safeName)
					.storageKey(storageKey)
					.mimeType(detectedMime)
					.fileSizeBytes((long) content.length)
					.checksumSha256(checksum)
					.documentType(documentType != null ? documentType : Receipt.DocumentType.RECEIPT)
					.description(trimToNull(description))
					.status(Receipt.ReceiptStatus.UPLOADED)
					.aiMetadata(metadata)
					.uploadedAt(Instant.now())
					.build();
			receipt.setFirmId(client.getFirmId());
			Receipt saved = receiptRepository.save(receipt);

			eventPublisher.publish(new DocumentUploadedEvent(saved.getId(), client.getId(), client.getFirmId()));
			auditLogger.record(AuditEvent.fromTenant()
					.firmId(saved.getFirmId())
					.action(AuditAction.DOCUMENT_UPLOADED)
					.resourceType(AuditResourceType.DOCUMENT)
					.resourceId(saved.getId())
					.clientId(clientId)
					.afterState(documentSnapshot(saved))
					.build());
			return toResponse(saved, existing != null, existing != null ? existing.getId() : null);
		} catch (RuntimeException ex) {
			try {
				fileStorageService.delete(storageKey);
			} catch (RuntimeException cleanup) {
				log.warn("Failed to clean stored object {} after persist error", storageKey);
			}
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public PageResponse<DocumentResponse> list(
			UUID clientId,
			Receipt.ReceiptStatus status,
			Receipt.DocumentType documentType,
			UUID uploadedBy,
			LocalDate from,
			LocalDate to,
			Boolean linked,
			int page,
			int size
	) {
		clientAccessService.requireReadAccess(clientId);
		return listInternal(List.of(clientId), status, documentType, uploadedBy, from, to, linked, page, size);
	}

	@Transactional(readOnly = true)
	public PageResponse<DocumentResponse> listInbox(
			UUID clientId,
			Receipt.ReceiptStatus status,
			Receipt.DocumentType documentType,
			UUID uploadedBy,
			LocalDate from,
			LocalDate to,
			Boolean linked,
			int page,
			int size
	) {
		if (clientId != null) {
			return list(clientId, status, documentType, uploadedBy, from, to, linked, page, size);
		}
		List<UUID> clientIds;
		if (clientAccessService.isAdmin()) {
			clientIds = List.of();
		} else {
			clientIds = List.copyOf(clientAccessService.accessibleClientIds());
			if (clientIds.isEmpty()) {
				return new PageResponse<>(List.of(), page, size, 0);
			}
		}
		return listInternal(clientIds, status, documentType, uploadedBy, from, to, linked, page, size);
	}

	private PageResponse<DocumentResponse> listInternal(
			List<UUID> clientIds,
			Receipt.ReceiptStatus status,
			Receipt.DocumentType documentType,
			UUID uploadedBy,
			LocalDate from,
			LocalDate to,
			Boolean linked,
			int page,
			int size
	) {
		SecurityUser current = SecurityUtils.requireCurrentUser();
		Specification<Receipt> spec = (root, query, cb) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("firmId"), current.getFirmId()));
			predicates.add(cb.isNull(root.get("deletedAt")));
			if (!clientIds.isEmpty()) {
				predicates.add(root.get("client").get("id").in(clientIds));
			}
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (documentType != null) {
				predicates.add(cb.equal(root.get("documentType"), documentType));
			}
			if (uploadedBy != null) {
				predicates.add(cb.equal(root.get("uploadedBy").get("id"), uploadedBy));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("uploadedAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
			}
			if (to != null) {
				predicates.add(cb.lessThan(root.get("uploadedAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
			}
			if (Boolean.TRUE.equals(linked)) {
				predicates.add(cb.equal(root.get("status"), Receipt.ReceiptStatus.LINKED));
			} else if (Boolean.FALSE.equals(linked)) {
				predicates.add(cb.notEqual(root.get("status"), Receipt.ReceiptStatus.LINKED));
			}
			return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
		if (current.getRole() == Role.RoleCode.BUSINESS_OWNER && !clientIds.isEmpty()
				&& clientIds.stream().allMatch(id -> restrictToOwnUploads(current, id))) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("uploadedBy").get("id"), current.getId()));
		} else if (current.getRole() == Role.RoleCode.BUSINESS_OWNER) {
			List<UUID> ownOnly = clientIds.stream().filter(id -> restrictToOwnUploads(current, id)).toList();
			if (!ownOnly.isEmpty() && ownOnly.size() == clientIds.size()) {
				spec = spec.and((root, query, cb) -> cb.equal(root.get("uploadedBy").get("id"), current.getId()));
			} else if (!ownOnly.isEmpty()) {
				spec = spec.and((root, query, cb) -> cb.or(
						cb.not(root.get("client").get("id").in(ownOnly)),
						cb.equal(root.get("uploadedBy").get("id"), current.getId())
				));
			}
		}
		if (current.getRole() == Role.RoleCode.AUDITOR) {
			spec = spec.and(this::finalizedEvidencePredicate);
		}

		Page<Receipt> results = receiptRepository.findAll(
				spec, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt")));
		return new PageResponse<>(
				results.getContent().stream().map(item -> toResponse(item, false, null)).toList(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public DocumentResponse get(UUID clientId, UUID documentId) {
		return toResponse(requireVisibleDocument(clientId, documentId), false, null);
	}

	@Transactional(readOnly = true)
	public StoredDocument openContent(UUID clientId, UUID documentId) {
		Receipt receipt = requireVisibleDocument(clientId, documentId);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(receipt.getFirmId())
				.action(AuditAction.DOCUMENT_DOWNLOADED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(clientId)
				.build());
		try {
			return new StoredDocument(receipt.getFileName(), receipt.getMimeType(), fileStorageService.open(receipt.getStorageKey()));
		} catch (BusinessException ex) {
			log.warn("Document {} firm {} storage provider {} failed: {}",
					receipt.getId(), receipt.getFirmId(), storageProperties.getProvider(), ex.getErrorCode());
			throw ex;
		}
	}

	@Transactional
	public DocumentResponse reject(UUID clientId, UUID documentId, String reason) {
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = requireDocument(clientId, documentId);
		if (receipt.getStatus() == Receipt.ReceiptStatus.LINKED) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_LINKED, "Linked documents cannot be rejected");
		}
		String reviewNote = requireReason(reason);
		receipt.setStatus(Receipt.ReceiptStatus.REJECTED);
		markReviewed(receipt, reviewNote);
		Receipt saved = receiptRepository.save(receipt);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_REJECTED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("reason", reviewNote, "status", saved.getStatus().name()))
				.build());
		return toResponse(saved, false, null);
	}

	@Transactional
	public DocumentResponse retryProcessing(UUID clientId, UUID documentId) {
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = requireDocument(clientId, documentId);
		if (receipt.getStatus() == Receipt.ReceiptStatus.LINKED || receipt.getStatus() == Receipt.ReceiptStatus.REJECTED) {
			throw new ValidationException("documentId", "Linked or rejected documents cannot be reprocessed");
		}
		if (receipt.getStatus() == Receipt.ReceiptStatus.PROCESSING
				&& receipt.getUpdatedAt() != null
				&& receipt.getUpdatedAt().isAfter(Instant.now().minusSeconds(90))) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_PROCESSING, "Document is already being processed");
		}
		long recent = attemptRepository.countByReceipt_IdAndCreatedAtAfter(documentId, Instant.now().minusSeconds(86400));
		if (recent >= 5) {
			throw new BusinessException(ErrorCodes.AI_RATE_LIMITED, "Too many processing retries for this document");
		}
		eventPublisher.publish(new DocumentUploadedEvent(receipt.getId(), clientId, receipt.getFirmId()));
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(receipt.getFirmId())
				.action(AuditAction.AI_PROCESSING_RETRIED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(receipt.getId())
				.clientId(clientId)
				.afterState(Map.of("status", receipt.getStatus().name()))
				.build());
		return toResponse(receipt, false, null);
	}

	@Transactional
	public DocumentResponse markDuplicate(UUID clientId, UUID documentId) {
		return reject(clientId, documentId, "Marked duplicate");
	}

	@Transactional
	public DocumentResponse linkToExpense(UUID clientId, UUID expenseId, UUID documentId) {
		return linkToTransaction(clientId, documentId, expenseId, null);
	}

	@Transactional
	public DocumentResponse linkToIncome(UUID clientId, UUID incomeId, UUID documentId) {
		return linkToTransaction(clientId, documentId, null, incomeId);
	}

	@Transactional
	public DocumentResponse unlink(UUID clientId, UUID documentId, UUID expenseId, UUID incomeId) {
		if (expenseId == null && incomeId == null) {
			throw new ValidationException("transactionId", "Specify the expense or income to unlink");
		}
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = requireDocument(clientId, documentId);
		if (expenseId != null) {
			Expense expense = requireFirmExpense(clientId, expenseId);
			assertUnlinkAllowed(expense.getStatus());
			if (expense.getStatus() == TransactionStatus.APPROVED || expense.getStatus() == TransactionStatus.VOID) {
				periodCloseService.assertPeriodOpen(clientId, expense.getTransactionDate());
			}
			expense.getReceipts().remove(receipt);
			if (expense.getPrimaryReceipt() != null && expense.getPrimaryReceipt().getId().equals(receipt.getId())) {
				expense.setPrimaryReceipt(expense.getReceipts().stream().findFirst().orElse(null));
			}
			expenseRepository.save(expense);
		}
		if (incomeId != null) {
			Income income = requireFirmIncome(clientId, incomeId);
			assertUnlinkAllowed(income.getStatus());
			if (income.getStatus() == TransactionStatus.APPROVED || income.getStatus() == TransactionStatus.VOID) {
				periodCloseService.assertPeriodOpen(clientId, income.getTransactionDate());
			}
			income.getReceipts().remove(receipt);
			if (income.getPrimaryReceipt() != null && income.getPrimaryReceipt().getId().equals(receipt.getId())) {
				income.setPrimaryReceipt(income.getReceipts().stream().findFirst().orElse(null));
			}
			incomeRepository.save(income);
		}
		receiptRepository.flush();
		Receipt fresh = requireDocument(clientId, documentId);
		boolean stillLinked = expenseRepository.countByReceiptId(fresh.getId()) > 0
				|| incomeRepository.countByReceiptId(fresh.getId()) > 0;
		if (!stillLinked) {
			fresh.setStatus(Receipt.ReceiptStatus.NEEDS_REVIEW);
		}
		Receipt saved = receiptRepository.save(fresh);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_UNLINKED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of(
						"expenseId", expenseId != null ? expenseId.toString() : "",
						"incomeId", incomeId != null ? incomeId.toString() : ""
				))
				.build());
		return toResponse(saved, false, null);
	}

	@Transactional
	public void delete(UUID clientId, UUID documentId) {
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = requireDocument(clientId, documentId);
		if (supportsFinalizedTransaction(receipt)) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_LINKED, "Documents supporting finalized transactions cannot be deleted");
		}
		if (receipt.getStatus() == Receipt.ReceiptStatus.LINKED) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_LINKED, "Unlink the document before deleting it");
		}
		String storageKey = receipt.getStorageKey();
		UUID firmId = receipt.getFirmId();
		receipt.setDeletedAt(Instant.now());
		receiptRepository.save(receipt);
		fileStorageService.delete(storageKey);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(firmId)
				.action(AuditAction.DOCUMENT_DELETED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(documentId)
				.clientId(clientId)
				.beforeState(documentSnapshot(receipt))
				.build());
	}

	public Receipt requireDocument(UUID clientId, UUID documentId) {
		clientAccessService.requireReadAccess(clientId);
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return receiptRepository.findByIdAndClient_IdAndFirmIdAndDeletedAtIsNull(documentId, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
	}

	private DocumentResponse linkToTransaction(UUID clientId, UUID documentId, UUID expenseId, UUID incomeId) {
		clientAccessService.requireWriteAccess(clientId);
		Receipt receipt = requireDocument(clientId, documentId);
		if (receipt.getStatus() == Receipt.ReceiptStatus.REJECTED) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Rejected documents cannot be linked");
		}
		if (receipt.getClient() == null || !clientId.equals(receipt.getClient().getId())) {
			throw new BusinessException(ErrorCodes.DOCUMENT_CLIENT_MISMATCH, "Document does not belong to this client");
		}
		Map<String, Object> after = new LinkedHashMap<>();
		if (expenseId != null) {
			Expense expense = requireFirmExpense(clientId, expenseId);
			assertSameFirm(expense.getFirmId(), receipt.getFirmId());
			expense.linkReceipt(receipt);
			expenseRepository.save(expense);
			after.put("expenseId", expenseId);
			after.put("transactionStatus", expense.getStatus().name());
			if (periodCloseService.isPeriodClosed(clientId, expense.getTransactionDate())) {
				after.put("postCloseEvidence", true);
			}
		}
		if (incomeId != null) {
			Income income = requireFirmIncome(clientId, incomeId);
			assertSameFirm(income.getFirmId(), receipt.getFirmId());
			income.linkReceipt(receipt);
			incomeRepository.save(income);
			after.put("incomeId", incomeId);
			after.put("transactionStatus", income.getStatus().name());
			if (periodCloseService.isPeriodClosed(clientId, income.getTransactionDate())) {
				after.put("postCloseEvidence", true);
			}
		}
		receipt.setStatus(Receipt.ReceiptStatus.LINKED);
		Receipt saved = receiptRepository.save(receipt);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.DOCUMENT_LINKED)
				.resourceType(AuditResourceType.DOCUMENT)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(after)
				.build());
		return toResponse(saved, false, null);
	}

	private Receipt requireVisibleDocument(UUID clientId, UUID documentId) {
		Receipt receipt = requireDocument(clientId, documentId);
		SecurityUser current = SecurityUtils.requireCurrentUser();
		if (!isVisibleToCurrentUser(receipt, current)) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ACCESS_DENIED, "Access denied to document");
		}
		return receipt;
	}

	private boolean isVisibleToCurrentUser(Receipt receipt, SecurityUser current) {
		if (restrictToOwnUploads(current, receipt.getClient().getId())
				&& (receipt.getUploadedBy() == null || !receipt.getUploadedBy().getId().equals(current.getId()))) {
			return false;
		}
		if (current.getRole() == Role.RoleCode.AUDITOR) {
			return supportsFinalizedTransaction(receipt);
		}
		return true;
	}

	private jakarta.persistence.criteria.Predicate finalizedEvidencePredicate(
			jakarta.persistence.criteria.Root<Receipt> root,
			jakarta.persistence.criteria.CriteriaQuery<?> query,
			jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		jakarta.persistence.criteria.Subquery<UUID> expenseIds = query.subquery(UUID.class);
		jakarta.persistence.criteria.Root<Expense> expense = expenseIds.from(Expense.class);
		jakarta.persistence.criteria.Join<Expense, Receipt> expenseReceipt = expense.join("receipts");
		expenseIds.select(expenseReceipt.get("id")).where(expense.get("status").in(
				TransactionStatus.APPROVED, TransactionStatus.VOID));

		jakarta.persistence.criteria.Subquery<UUID> incomeIds = query.subquery(UUID.class);
		jakarta.persistence.criteria.Root<Income> income = incomeIds.from(Income.class);
		jakarta.persistence.criteria.Join<Income, Receipt> incomeReceipt = income.join("receipts");
		incomeIds.select(incomeReceipt.get("id")).where(income.get("status").in(
				TransactionStatus.APPROVED, TransactionStatus.VOID));
		return cb.or(root.get("id").in(expenseIds), root.get("id").in(incomeIds));
	}

	private boolean supportsFinalizedTransaction(Receipt receipt) {
		List<TransactionStatus> finalized = List.of(TransactionStatus.APPROVED, TransactionStatus.VOID);
		return expenseRepository.existsByReceiptIdAndStatusIn(receipt.getId(), finalized)
				|| incomeRepository.existsByReceiptIdAndStatusIn(receipt.getId(), finalized);
	}

	private boolean restrictToOwnUploads(SecurityUser current, UUID clientId) {
		if (current.getRole() == Role.RoleCode.ADMIN || current.getRole() == Role.RoleCode.ACCOUNTANT
				|| current.getRole() == Role.RoleCode.AUDITOR) {
			return false;
		}
		return clientAccessService.effectiveAccessType(clientId) == UserClientAccess.AccessType.UPLOAD_ONLY;
	}

	private Expense requireFirmExpense(UUID clientId, UUID expenseId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		Expense expense = expenseRepository.findByIdAndClient_IdAndFirmId(expenseId, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
		if (expense.getClient() == null || !clientId.equals(expense.getClient().getId())) {
			throw new BusinessException(ErrorCodes.DOCUMENT_CLIENT_MISMATCH, "Transaction does not belong to this client");
		}
		return expense;
	}

	private Income requireFirmIncome(UUID clientId, UUID incomeId) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		Income income = incomeRepository.findByIdAndClient_IdAndFirmId(incomeId, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
		if (income.getClient() == null || !clientId.equals(income.getClient().getId())) {
			throw new BusinessException(ErrorCodes.DOCUMENT_CLIENT_MISMATCH, "Transaction does not belong to this client");
		}
		return income;
	}

	private void assertUnlinkAllowed(TransactionStatus status) {
		if (status == TransactionStatus.VOID) {
			throw new BusinessException(ErrorCodes.DOCUMENT_ALREADY_LINKED, "Evidence on voided transactions cannot be unlinked");
		}
	}

	private void markReviewed(Receipt receipt, String note) {
		receipt.setReviewedBy(clientAccessService.requireCurrentUserEntity());
		receipt.setReviewedAt(Instant.now());
		receipt.setReviewNote(note);
	}

	private void validateFile(byte[] content, String originalFilename) {
		if (content == null || content.length == 0) {
			throw new ValidationException("file", "File content is required");
		}
		if (content.length > storageProperties.getMaxFileSizeBytes()) {
			throw new ValidationException(ErrorCodes.FILE_TOO_LARGE, "file", "File exceeds the maximum upload size");
		}
		String extension = extensionOf(originalFilename);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new ValidationException(ErrorCodes.UNSUPPORTED_FILE_TYPE, "file", "File type is not permitted");
		}
		if (!mimeMatches(content, extension)) {
			throw new ValidationException(ErrorCodes.UNSUPPORTED_FILE_TYPE, "file", "File content does not match the declared type");
		}
	}

	private static boolean mimeMatches(byte[] content, String extension) {
		if ("pdf".equals(extension)) {
			return content.length >= 4 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
		}
		if ("jpg".equals(extension) || "jpeg".equals(extension)) {
			return content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
		}
		if ("png".equals(extension)) {
			return content.length >= 4 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
		}
		if ("webp".equals(extension)) {
			return content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F';
		}
		return "csv".equals(extension);
	}

	private static String detectMimeType(byte[] content, String declared, String filename) {
		String extension = extensionOf(filename);
		if ("pdf".equals(extension)) {
			return "application/pdf";
		}
		if ("jpg".equals(extension) || "jpeg".equals(extension)) {
			return "image/jpeg";
		}
		if ("png".equals(extension)) {
			return "image/png";
		}
		if ("webp".equals(extension)) {
			return "image/webp";
		}
		if ("csv".equals(extension)) {
			return "text/csv";
		}
		if (declared != null && !declared.isBlank() && !"application/octet-stream".equalsIgnoreCase(declared)) {
			return declared;
		}
		return "application/octet-stream";
	}

	private static String sanitizeFilename(String originalFilename) {
		String name = originalFilename == null ? "document" : originalFilename.replace('\\', '/');
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (name.isBlank() || name.startsWith(".")) {
			name = "document";
		}
		if (name.length() > 120) {
			name = name.substring(name.length() - 120);
		}
		return name;
	}

	private static String extensionOf(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available");
		}
	}

	private static void assertSameFirm(UUID left, UUID right) {
		if (left == null || !left.equals(right)) {
			throw new AccessDeniedException("Cross-firm document linking is not permitted");
		}
	}

	private static String requireReason(String reason) {
		String trimmed = reason == null ? "" : reason.trim();
		if (trimmed.isBlank()) {
			throw new ValidationException("reason", "A rejection reason is required");
		}
		return trimmed;
	}

	private void assertUploadAllowed(UUID firmId, long incomingBytes) {
		try {
			subscriptionQuotaGuard.assertCanUploadDocument(firmId, incomingBytes);
		} catch (BusinessException ex) {
			SecurityUser user = SecurityUtils.requireCurrentUser();
			if (user.getRole() == Role.RoleCode.BUSINESS_OWNER) {
				throw new BusinessException(ErrorCodes.UPLOADS_UNAVAILABLE,
						"Document uploads are temporarily unavailable. Please contact your accounting firm.");
			}
			throw ex;
		}
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private DocumentResponse toResponse(Receipt receipt, boolean possibleDuplicate, UUID existingDocumentId) {
		AiExtractionMetadata ai = receipt.getAiMetadata() != null ? receipt.getAiMetadata() : new AiExtractionMetadata();
		UUID scopedClientId = receipt.getClient() != null ? receipt.getClient().getId() : null;
		boolean hideReasoning = SecurityUtils.requireCurrentUser().getRole() == Role.RoleCode.AUDITOR
				|| (scopedClientId != null && clientAccessService.effectiveAccessType(scopedClientId) == UserClientAccess.AccessType.UPLOAD_ONLY);
		return new DocumentResponse(
				receipt.getId(),
				receipt.getFirmId(),
				receipt.getClient() != null ? receipt.getClient().getId() : null,
				receipt.getClient() != null ? receipt.getClient().getName() : null,
				receipt.getUploadedBy() != null ? receipt.getUploadedBy().getId() : null,
				receipt.getUploadedBy() != null ? receipt.getUploadedBy().getFullName() : null,
				receipt.getDescription(),
				receipt.getFileName(),
				receipt.getMimeType(),
				receipt.getFileSizeBytes(),
				receipt.getChecksumSha256(),
				receipt.getDocumentType(),
				receipt.getStatus(),
				ai.getExtractionStatus(),
				ai.getConfidenceScore(),
				confidenceLabel(ai.getConfidenceScore()),
				ai.getSuggestedType() != null ? ai.getSuggestedType().name() : null,
				ai.getSuggestedVendorOrCustomer(),
				ai.getSuggestedDate(),
				ai.getSuggestedAmount(),
				ai.getSuggestedSubtotal(),
				ai.getSuggestedTaxAmount(),
				ai.getSuggestedCurrency(),
				ai.getSuggestedInvoiceNo(),
				ai.getSuggestedDueDate(),
				ai.getSuggestedPaymentMethod(),
				ai.getSuggestedDescription(),
				receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getId() : null,
				receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getName() : null,
				receipt.getSuggestedCategory() != null ? receipt.getSuggestedCategory().getCode() : null,
				hideReasoning ? null : ai.getOcrText(),
				possibleDuplicate,
				existingDocumentId,
				receipt.getExpenses() == null ? List.of() : receipt.getExpenses().stream().map(Expense::getId).toList(),
				receipt.getIncomes() == null ? List.of() : receipt.getIncomes().stream().map(Income::getId).toList(),
				receipt.getReviewedBy() != null ? receipt.getReviewedBy().getId() : null,
				receipt.getReviewedAt(),
				receipt.getReviewNote(),
				ai.getReviewOutcome(),
				ai.getFailureCode(),
				ai.getFailureMessage(),
				ai.getAmountInconsistency(),
				ai.getDateWarning(),
				ai.getSupplierConfidence(),
				ai.getDateConfidence(),
				ai.getAmountConfidence(),
				ai.getTaxConfidence(),
				ai.getAiProvider(),
				ai.getModelVersion(),
				receipt.getUploadedAt(),
				receipt.getCreatedAt()
		);
	}

	private static String confidenceLabel(java.math.BigDecimal score) {
		if (score == null) {
			return "Unknown";
		}
		if (score.compareTo(new java.math.BigDecimal("0.80")) >= 0) {
			return "High";
		}
		if (score.compareTo(new java.math.BigDecimal("0.50")) >= 0) {
			return "Medium";
		}
		return "Low";
	}

	private static Map<String, Object> documentSnapshot(Receipt receipt) {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("fileName", receipt.getFileName());
		state.put("documentType", receipt.getDocumentType() != null ? receipt.getDocumentType().name() : null);
		state.put("status", receipt.getStatus() != null ? receipt.getStatus().name() : null);
		state.put("checksumSha256", receipt.getChecksumSha256());
		return state;
	}

	public record StoredDocument(String fileName, String mimeType, InputStream content) {
	}
}
