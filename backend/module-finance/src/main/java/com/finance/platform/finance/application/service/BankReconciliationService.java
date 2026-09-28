package com.finance.platform.finance.application.service;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.storage.FileStorageService;
import com.finance.platform.finance.application.bankimport.BankStatementImporter;
import com.finance.platform.finance.application.bankimport.BankTransactionFingerprint;
import com.finance.platform.finance.application.bankimport.CsvColumnMapping;
import com.finance.platform.finance.application.bankimport.ImportParseResult;
import com.finance.platform.finance.application.bankimport.ImportPreview;
import com.finance.platform.finance.application.bankimport.ParsedBankRow;
import com.finance.platform.finance.application.dto.BankImportPreviewResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
import com.finance.platform.finance.application.dto.ConfirmBankInvoicePaymentRequest;
import com.finance.platform.finance.application.dto.CreateDocumentRequestFromBankRequest;
import com.finance.platform.finance.application.dto.CreateExpenseFromBankRequest;
import com.finance.platform.finance.application.dto.CreateExpenseRequest;
import com.finance.platform.finance.application.dto.CreateIncomeFromBankRequest;
import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeRequest;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.application.dto.MatchSuggestionResponse;
import com.finance.platform.finance.application.dto.ReconciliationSummaryResponse;
import com.finance.platform.finance.domain.model.BankAccount;
import com.finance.platform.finance.domain.model.BankImport;
import com.finance.platform.finance.domain.model.BankImportProfile;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.BankTransactionLedgerGeneration;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.application.dto.invoicing.AllocatePaymentRequest;
import com.finance.platform.finance.application.dto.invoicing.ArPaymentResponse;
import com.finance.platform.finance.application.dto.invoicing.PaymentAllocationItemRequest;
import com.finance.platform.finance.application.dto.invoicing.ReversePaymentRequest;
import com.finance.platform.finance.application.invoicing.MoneyMath;
import com.finance.platform.finance.application.service.invoicing.ArPaymentService;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import com.finance.platform.finance.domain.model.invoicing.ArPayment;
import com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroup;
import com.finance.platform.finance.domain.model.recon.ReconciliationMatchGroupItem;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.ArPaymentJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankImportJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankImportProfileJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionLedgerGenerationJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.application.workflow.PeriodReadinessNotifier;
import com.finance.platform.finance.application.workflow.WorkflowNotificationService;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchGroupItemJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchGroupJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankReconciliationService {

	private final BankImportJpaRepository importRepository;
	private final BankImportProfileJpaRepository profileRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;
	private final ReconciliationMatchJpaRepository matchRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final BankAccountService bankAccountService;
	private final ClientAccessService clientAccessService;
	private final PeriodCloseService periodCloseService;
	private final FileStorageService fileStorageService;
	private final BankStatementImporter bankStatementImporter;
	private final ReconciliationSuggestionService suggestionService;
	private final ExpenseService expenseService;
	private final IncomeService incomeService;
	private final DocumentRequestService documentRequestService;
	private final AuditLogger auditLogger;
	private final WorkflowNotificationService workflowNotificationService;
	private final ArPaymentService arPaymentService;
	private final ReconciliationMatchGroupJpaRepository matchGroupRepository;
	private final ReconciliationMatchGroupItemJpaRepository matchGroupItemRepository;
	private final PeriodReadinessNotifier periodReadinessNotifier;
	private final BankImportTransactionWriter bankImportTransactionWriter;
	private final BankImportBatchWriter bankImportBatchWriter;
	private final BankTransactionLedgerGenerationJpaRepository ledgerGenerationRepository;
	private final ArPaymentJpaRepository arPaymentRepository;

	@Transactional(readOnly = true)
	public BankImportPreviewResponse previewImport(UUID clientId, UUID bankAccountId, byte[] content, CsvColumnMapping mapping) {
		clientAccessService.requireWriteAccess(clientId);
		bankAccountService.requireAccount(clientId, bankAccountId);
		ImportPreview preview = bankStatementImporter.preview(content, mapping);
		return BankImportPreviewResponse.from(preview);
	}

	@Transactional(noRollbackFor = DataIntegrityViolationException.class)
	public BankImportResponse importCsv(
			UUID clientId,
			UUID bankAccountId,
			String fileName,
			byte[] content,
			CsvColumnMapping mapping,
			String profileName
	) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		BankAccount account = bankAccountService.requireAccount(clientId, bankAccountId);
		if (content == null || content.length == 0) {
			throw new ValidationException("file", "CSV content is required");
		}
		String checksum = sha256(content);
		if (profileName != null && !profileName.isBlank()) {
			saveProfile(client, account, mapping, profileName.trim());
		}
		ImportParseResult parsed = bankStatementImporter.parse(content, mapping);
		if (parsed.validRows().isEmpty()) {
			throw new BusinessException(ErrorCodes.BANK_IMPORT_INVALID, "No valid rows found in the statement");
		}
		LocalDate periodFrom = null;
		LocalDate periodTo = null;
		for (ParsedBankRow row : parsed.validRows()) {
			if (periodFrom == null || row.txnDate().isBefore(periodFrom)) {
				periodFrom = row.txnDate();
			}
			if (periodTo == null || row.txnDate().isAfter(periodTo)) {
				periodTo = row.txnDate();
			}
		}

		Optional<BankImport> priorIdenticalFile = bankImportBatchWriter.findByAccountAndChecksum(bankAccountId, checksum);
		boolean identicalFileReplay = priorIdenticalFile.isPresent();
		BankImport batch;
		if (identicalFileReplay) {
			batch = priorIdenticalFile.get();
		} else {
			String storageKey = "firms/" + client.getFirmId() + "/clients/" + client.getId() + "/bank/" + UUID.randomUUID() + ".csv";
			fileStorageService.store(storageKey, content, "text/csv");
			batch = BankImport.builder()
					.client(client)
					.bankAccount(account)
					.uploadedBy(clientAccessService.requireCurrentUserEntity())
					.fileName(fileName == null ? "statement.csv" : fileName)
					.storageKey(storageKey)
					.checksum(checksum)
					.periodFrom(periodFrom)
					.periodTo(periodTo)
					.importStatus(BankImport.ImportStatus.IMPORTED)
					.status("IMPORTED")
					.build();
			batch.setFirmId(client.getFirmId());
			BankImport persisted = bankImportBatchWriter.tryPersist(batch);
			if (persisted == null) {
				batch = bankImportBatchWriter.findByAccountAndChecksum(bankAccountId, checksum)
						.orElseThrow(() -> new BusinessException(ErrorCodes.BANK_IMPORT_DUPLICATE,
								"This statement file was already imported for this bank account"));
				identicalFileReplay = true;
			} else {
				batch = importRepository.findById(persisted.getId())
						.orElseThrow(() -> new ResourceNotFoundException("BankImport", persisted.getId()));
			}
		}

		Set<String> lookupHashes = new HashSet<>();
		for (ParsedBankRow row : parsed.validRows()) {
			lookupHashes.add(row.rowHash());
			lookupHashes.add(legacyRowHash(row));
		}
		Set<String> existingHashes = lookupHashes.isEmpty()
				? Set.of()
				: bankTransactionRepository.findExternalRowHashesByBankAccount_IdAndExternalRowHashIn(
						bankAccountId, lookupHashes);
		Set<String> seenThisImport = new HashSet<>();

		int imported = 0;
		int duplicates = 0;
		int failed = parsed.errors().size();
		for (ParsedBankRow row : parsed.validRows()) {
			String rowHash = row.rowHash();
			String legacyHash = legacyRowHash(row);
			if (isDuplicateRow(rowHash, legacyHash, existingHashes, seenThisImport)) {
				duplicates++;
				continue;
			}
			BankTransaction txn = toEntity(client, account, batch, row, rowHash);
			BankTransaction persistedTxn;
			try {
				persistedTxn = bankImportTransactionWriter.tryPersist(txn);
			} catch (DataIntegrityViolationException ex) {
				persistedTxn = null;
			}
			if (persistedTxn == null) {
				duplicates++;
				existingHashes.add(rowHash);
				existingHashes.add(legacyHash);
				continue;
			}
			UUID persistedTxnId = persistedTxn.getId();
			seenThisImport.add(rowHash);
			seenThisImport.add(legacyHash);
			existingHashes.add(rowHash);
			BankTransaction managedTxn = bankTransactionRepository.findById(persistedTxnId)
					.orElseThrow(() -> new ResourceNotFoundException("BankTransaction", persistedTxnId));
			try {
				generateSuggestions(managedTxn);
			} catch (DataIntegrityViolationException ex) {
				duplicates++;
				continue;
			}
			imported++;
		}

		if (!identicalFileReplay) {
			batch.setRowCount(parsed.validRows().size() + failed);
			batch.setImportedCount(imported);
			batch.setDuplicateCount(duplicates);
			batch.setFailedCount(failed);
			batch.setCompletedAt(Instant.now());
			batch = importRepository.save(batch);
		}
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.BANK_IMPORT_COMPLETED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(batch.getId())
				.clientId(clientId)
				.afterState(Map.of(
						"imported", imported,
						"duplicates", duplicates,
						"failed", failed,
						"identicalFileReplay", identicalFileReplay))
				.build());
		if (!identicalFileReplay) {
			workflowNotificationService.bankImportCompleted(client.getFirmId(), clientId, batch.getId());
			periodReadinessNotifier.checkAndNotify(client.getFirmId(), clientId);
		}
		if (identicalFileReplay) {
			return idempotentFileReplayResponse(batch, clientId, bankAccountId, parsed, imported, duplicates, failed);
		}
		return BankImportResponse.from(batch);
	}

	private static boolean isDuplicateRow(
			String rowHash,
			String legacyHash,
			Set<String> existingHashes,
			Set<String> seenThisImport
	) {
		return existingHashes.contains(rowHash)
				|| existingHashes.contains(legacyHash)
				|| !seenThisImport.add(rowHash);
	}

	private static String legacyRowHash(ParsedBankRow row) {
		return BankTransactionFingerprint.legacyHeuristicFingerprint(
				row.txnDate(),
				row.valueDate(),
				row.debit(),
				row.credit(),
				row.description(),
				row.referenceNo());
	}

	private static BankImportResponse idempotentFileReplayResponse(
			BankImport priorBatch,
			UUID clientId,
			UUID bankAccountId,
			ImportParseResult parsed,
			int imported,
			int duplicates,
			int failed
	) {
		return new BankImportResponse(
				priorBatch.getId(),
				clientId,
				bankAccountId,
				priorBatch.getFileName(),
				priorBatch.getChecksum(),
				priorBatch.getPeriodFrom(),
				priorBatch.getPeriodTo(),
				priorBatch.getImportStatus(),
				parsed.validRows().size() + failed,
				imported,
				duplicates,
				failed,
				priorBatch.getErrorMessage(),
				priorBatch.getCreatedAt(),
				priorBatch.getCompletedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<BankTransactionResponse> list(
			UUID clientId,
			UUID bankAccountId,
			BankTransaction.MatchStatus status,
			LocalDate from,
			LocalDate to,
			String query,
			int page,
			int size
	) {
		clientAccessService.requireReadAccess(clientId);
		int capped = Math.min(Math.max(size, 1), 100);
		Pageable pageable = PageRequest.of(page, capped, Sort.by(Sort.Direction.DESC, "txnDate"));
		String q = blankToNull(query);
		Page<BankTransaction> results = q == null
				? bankTransactionRepository.searchFiltered(clientId, bankAccountId, status, from, to, pageable)
				: bankTransactionRepository.searchWithText(clientId, bankAccountId, status, from, to, q, pageable);
		return new PageResponse<>(
				results.getContent().stream().map(this::toResponse).toList(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Map<String, Long> dashboard(UUID clientId) {
		clientAccessService.requireReadAccess(clientId);
		Map<String, Long> counts = new LinkedHashMap<>();
		for (BankTransaction.MatchStatus status : BankTransaction.MatchStatus.values()) {
			counts.put(status.name(), bankTransactionRepository.countByClient_IdAndMatchStatus(clientId, status));
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public ReconciliationSummaryResponse summary(UUID clientId, UUID bankAccountId, LocalDate from, LocalDate to) {
		clientAccessService.requireReadAccess(clientId);
		List<BankTransaction> rows = bankTransactionRepository.findByClient_IdAndTxnDateBetween(clientId, from, to).stream()
				.filter(txn -> bankAccountId == null || (txn.getBankAccount() != null && bankAccountId.equals(txn.getBankAccount().getId())))
				.toList();
		long matched = rows.stream().filter(txn -> txn.getMatchStatus() == BankTransaction.MatchStatus.MATCHED).count();
		long suggested = rows.stream().filter(txn -> txn.getMatchStatus() == BankTransaction.MatchStatus.SUGGESTED).count();
		long unmatched = rows.stream().filter(txn -> txn.getMatchStatus() == BankTransaction.MatchStatus.UNMATCHED).count();
		long ignored = rows.stream().filter(txn -> txn.getMatchStatus() == BankTransaction.MatchStatus.IGNORED).count();
		long pending = rows.stream().filter(txn -> txn.getMatchStatus() == BankTransaction.MatchStatus.PENDING_APPROVAL).count();
		BigDecimal debits = BigDecimal.ZERO;
		BigDecimal credits = BigDecimal.ZERO;
		BigDecimal matchedValue = BigDecimal.ZERO;
		BigDecimal unmatchedValue = BigDecimal.ZERO;
		for (BankTransaction txn : rows) {
			if (txn.getDebit() != null) {
				debits = debits.add(txn.getDebit());
			}
			if (txn.getCredit() != null) {
				credits = credits.add(txn.getCredit());
			}
			BigDecimal amount = txn.absoluteAmount();
			if (txn.getMatchStatus() == BankTransaction.MatchStatus.MATCHED || txn.getMatchStatus() == BankTransaction.MatchStatus.IGNORED) {
				matchedValue = matchedValue.add(amount);
			} else if (txn.getMatchStatus() != BankTransaction.MatchStatus.IGNORED) {
				unmatchedValue = unmatchedValue.add(amount);
			}
		}
		int percent = com.finance.platform.finance.application.banking.ReconciliationProgressPercent.compute(
				rows.size(), matched, ignored);
		return new ReconciliationSummaryResponse(
				rows.size(), matched, suggested, unmatched, ignored, pending,
				debits, credits, matchedValue, unmatchedValue, percent);
	}

	@Transactional
	public BankTransactionResponse confirmMatch(UUID clientId, UUID bankTransactionId, UUID expenseId, UUID incomeId) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		assertBankAvailableForLedgerMatch(bank, expenseId, incomeId);
		ReconciliationMatch match = ReconciliationMatch.builder()
				.client(bank.getClient())
				.bankTransaction(bank)
				.status(ReconciliationMatch.MatchStatus.CONFIRMED)
				.confirmedBy(clientAccessService.requireCurrentUserEntity())
				.confirmedAt(Instant.now())
				.build();
		match.setFirmId(bank.getFirmId());
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		if (expenseId != null) {
			Expense expense = expenseRepository.findByIdAndClient_IdAndFirmId(expenseId, clientId, firmId)
					.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
			validateDirection(bank, true);
			validateLedgerMatch(expense.getStatus(), expense.getAmount(), bank.absoluteAmount());
			if (matchRepository.isLedgerEntryMatched(expense.getId(), null)) {
				throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Expense is already reconciled");
			}
			match.setExpense(expense);
		} else if (incomeId != null) {
			Income income = incomeRepository.findByIdAndClient_IdAndFirmId(incomeId, clientId, firmId)
					.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
			validateDirection(bank, false);
			validateLedgerMatch(income.getStatus(), income.getAmount(), bank.absoluteAmount());
			if (matchRepository.isLedgerEntryMatched(null, income.getId())) {
				throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Income is already reconciled");
			}
			match.setIncome(income);
		} else {
			throw new ValidationException("A matching expense or income is required");
		}
		rejectOpenSuggestions(bank);
		persistConfirmedMatch(match);
		bank.setMatchStatus(BankTransaction.MatchStatus.MATCHED);
		bank.setPendingExpenseId(null);
		bank.setPendingIncomeId(null);
		BankTransaction saved = bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_CONFIRMED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.build());
		return toResponse(saved);
	}

	@Transactional
	public BankTransactionResponse confirmInvoicePayment(
			UUID clientId,
			UUID bankTransactionId,
			ConfirmBankInvoicePaymentRequest request
	) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.MATCHED) {
			return toResponse(bank);
		}
		assertBankAvailableForInvoicePayment(bank);
		validateDirection(bank, false);
		BigDecimal bankAmount = bank.absoluteAmount();
		List<PaymentAllocationItemRequest> allocations = request.allocations() == null
				? List.of()
				: request.allocations();
		BigDecimal allocationTotal = BigDecimal.ZERO;
		for (PaymentAllocationItemRequest item : allocations) {
			allocationTotal = MoneyMath.add(allocationTotal, MoneyMath.money(item.amount()));
		}
		if (allocationTotal.compareTo(bankAmount) > 0) {
			throw new ValidationException("allocations", "Allocation total exceeds bank transaction amount");
		}
		String reference = bank.getReferenceNo() == null ? bank.getDescription() : bank.getReferenceNo();
		ArPaymentResponse payment = arPaymentService.recordFromBank(
				request.customerId(),
				bank.getTxnDate(),
				bankAmount,
				reference,
				bank.getId());
		if (!allocations.isEmpty()) {
			payment = arPaymentService.allocate(payment.id(), new AllocatePaymentRequest(allocations));
		}
		ReconciliationMatchGroup group = ReconciliationMatchGroup.builder()
				.client(bank.getClient())
				.status(ReconciliationMatchGroup.Status.CONFIRMED)
				.confirmedBy(SecurityUtils.requireCurrentUser().getId())
				.confirmedAt(Instant.now())
				.notes("Invoice payment match")
				.build();
		group.setFirmId(bank.getFirmId());
		group = matchGroupRepository.save(group);
		try {
			matchGroupItemRepository.saveAndFlush(ReconciliationMatchGroupItem.builder()
					.group(group)
					.bankTransaction(bank)
					.bankClaimActive(true)
					.build());
		} catch (DataIntegrityViolationException ex) {
			throw translateReconciliationUniqueViolation(ex);
		}
		matchGroupItemRepository.save(ReconciliationMatchGroupItem.builder()
				.group(group)
				.paymentId(payment.id())
				.allocatedAmount(bankAmount)
				.build());
		for (PaymentAllocationItemRequest item : allocations) {
			matchGroupItemRepository.save(ReconciliationMatchGroupItem.builder()
					.group(group)
					.invoiceId(item.invoiceId())
					.allocatedAmount(MoneyMath.money(item.amount()))
					.build());
		}
		rejectOpenSuggestions(bank);
		bank.setMatchStatus(BankTransaction.MatchStatus.MATCHED);
		bank.setPendingExpenseId(null);
		bank.setPendingIncomeId(null);
		BankTransaction saved = bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_CONFIRMED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.metadata(Map.of("event", "bank_invoice_payment", "paymentId", payment.id().toString()))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public BankTransactionResponse rejectSuggestion(UUID clientId, UUID bankTransactionId, UUID matchId) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		ReconciliationMatch match = matchRepository.findById(matchId)
				.filter(row -> row.getBankTransaction().getId().equals(bankTransactionId))
				.orElseThrow(() -> new ResourceNotFoundException("Reconciliation match", matchId));
		match.setStatus(ReconciliationMatch.MatchStatus.REJECTED);
		matchRepository.save(match);
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.SUGGESTED) {
			boolean hasSuggestions = matchRepository.findActiveSuggestions(bankTransactionId).stream()
					.anyMatch(row -> row.getStatus() == ReconciliationMatch.MatchStatus.SUGGESTED);
			if (!hasSuggestions) {
				bank.setMatchStatus(BankTransaction.MatchStatus.UNMATCHED);
			}
		}
		BankTransaction saved = bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_REJECTED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.build());
		return toResponse(saved);
	}

	@Transactional
	public BankTransactionResponse unmatch(UUID clientId, UUID bankTransactionId) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		UUID reversedPaymentId = reverseActiveInvoicePaymentForBank(clientId, firmId, bankTransactionId);
		releaseActiveInvoiceMatchGroup(firmId, bankTransactionId);
		for (ReconciliationMatch match : matchRepository.findByBankTransaction_Id(bankTransactionId)) {
			if (match.getStatus() == ReconciliationMatch.MatchStatus.CONFIRMED) {
				match.setStatus(ReconciliationMatch.MatchStatus.REJECTED);
				match.setNotes("Unmatched by user");
				matchRepository.save(match);
			}
		}
		bank.setMatchStatus(BankTransaction.MatchStatus.UNMATCHED);
		bank.setPendingExpenseId(null);
		bank.setPendingIncomeId(null);
		BankTransaction saved = bankTransactionRepository.save(bank);
		generateSuggestions(saved);
		Map<String, Object> auditMeta = new LinkedHashMap<>();
		if (reversedPaymentId != null) {
			auditMeta.put("event", "bank_invoice_payment_reversed");
			auditMeta.put("paymentId", reversedPaymentId.toString());
		}
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_REMOVED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.metadata(auditMeta.isEmpty() ? null : auditMeta)
				.build());
		return toResponse(saved);
	}

	/**
	 * When a reconciled ledger entry is voided, release confirmed bank matches so close readiness stays consistent.
	 */
	@Transactional
	public void releaseConfirmedMatchesForVoidedExpense(UUID clientId, UUID expenseId) {
		for (ReconciliationMatch match : matchRepository.findConfirmedByExpenseId(expenseId)) {
			if (match.getBankTransaction() == null) {
				continue;
			}
			unmatch(clientId, match.getBankTransaction().getId());
		}
	}

	@Transactional
	public BankTransactionResponse ignore(UUID clientId, UUID bankTransactionId, String reason) {
		clientAccessService.requireApproveAccess(clientId);
		if (reason == null || reason.isBlank()) {
			throw new ValidationException("reason", "An ignore reason is required");
		}
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		rejectOpenSuggestions(bank);
		bank.setMatchStatus(BankTransaction.MatchStatus.IGNORED);
		bank.setIgnoreReason(reason.trim());
		BankTransaction saved = bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.BANK_TRANSACTION_IGNORED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.afterState(Map.of("reason", reason.trim()))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public ExpenseResponse createExpenseFromBank(UUID clientId, UUID bankTransactionId, CreateExpenseFromBankRequest request) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		assertEligibleForLedgerGeneration(bank);
		validateDirection(bank, true);
		assertNoLedgerGeneration(bank.getId());
		rejectOpenSuggestions(bank);
		ExpenseResponse created = expenseService.create(clientId, new CreateExpenseRequest(
				bank.getTxnDate(),
				request.categoryId(),
				bank.absoluteAmount(),
				bank.getCurrency(),
				request.vendorName() == null || request.vendorName().isBlank()
						? defaultParty(bank.getDescription(), "Vendor")
						: request.vendorName(),
				request.description() == null ? bank.getDescription() : request.description(),
				null,
				bank.getReferenceNo()));
		persistLedgerGeneration(bank, BankTransactionLedgerGeneration.LedgerKind.EXPENSE, created.id(), null);
		bank.setPendingExpenseId(created.id());
		bank.setPendingIncomeId(null);
		bank.setMatchStatus(BankTransaction.MatchStatus.PENDING_APPROVAL);
		bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.TRANSACTION_CREATED_FROM_BANK)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.afterState(Map.of("expenseId", created.id().toString(), "type", "EXPENSE"))
				.build());
		return created;
	}

	@Transactional
	public IncomeResponse createIncomeFromBank(UUID clientId, UUID bankTransactionId, CreateIncomeFromBankRequest request) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBankForUpdate(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		assertEligibleForLedgerGeneration(bank);
		validateDirection(bank, false);
		assertNoLedgerGeneration(bank.getId());
		rejectOpenSuggestions(bank);
		IncomeResponse created = incomeService.create(clientId, new IncomeRequest(
				bank.getTxnDate(),
				request.categoryId(),
				bank.absoluteAmount(),
				bank.getCurrency(),
				request.payerName() == null || request.payerName().isBlank()
						? defaultParty(bank.getDescription(), "Customer")
						: request.payerName(),
				request.description() == null ? bank.getDescription() : request.description(),
				null,
				null,
				bank.getReferenceNo()));
		persistLedgerGeneration(bank, BankTransactionLedgerGeneration.LedgerKind.INCOME, null, created.id());
		bank.setPendingIncomeId(created.id());
		bank.setPendingExpenseId(null);
		bank.setMatchStatus(BankTransaction.MatchStatus.PENDING_APPROVAL);
		bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.TRANSACTION_CREATED_FROM_BANK)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.afterState(Map.of("incomeId", created.id().toString(), "type", "INCOME"))
				.build());
		return created;
	}

	@Transactional
	public DocumentRequest createDocumentRequestFromBank(
			UUID clientId,
			UUID bankTransactionId,
			CreateDocumentRequestFromBankRequest request
	) {
		clientAccessService.requireWriteAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		String description = request.description() == null || request.description().isBlank()
				? "Supporting document for bank transaction " + bank.getTxnDate() + " " + bank.absoluteAmount()
				: request.description();
		Receipt.DocumentType type = parseDocumentType(request.documentType());
		DocumentRequest saved = documentRequestService.create(
				clientId,
				null,
				description,
				type,
				request.dueDate(),
				null,
				null,
				request.periodId());
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.DOCUMENT_REQUEST_CREATED_FROM_BANK)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.afterState(Map.of("documentRequestId", saved.getId().toString()))
				.build());
		return saved;
	}

	@Transactional
	public BankTransactionResponse regenerateSuggestions(UUID clientId, UUID bankTransactionId) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		rejectOpenSuggestions(bank);
		generateSuggestions(bank);
		return toResponse(requireBank(clientId, bankTransactionId));
	}

	@Transactional(readOnly = true)
	public List<BankImportResponse> listImports(UUID clientId, UUID bankAccountId) {
		clientAccessService.requireReadAccess(clientId);
		List<BankImport> imports = bankAccountId == null
				? importRepository.findByClient_IdOrderByCreatedAtDesc(clientId)
				: importRepository.findByClient_IdAndBankAccount_IdOrderByCreatedAtDesc(clientId, bankAccountId);
		return imports.stream().map(BankImportResponse::from).toList();
	}

	private void generateSuggestions(BankTransaction bank) {
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.MATCHED
				|| bank.getMatchStatus() == BankTransaction.MatchStatus.IGNORED
				|| bank.getMatchStatus() == BankTransaction.MatchStatus.PENDING_APPROVAL) {
			return;
		}
		List<MatchSuggestionResponse> suggestions = suggestionService.suggest(bank);
		if (suggestions.isEmpty()) {
			bank.setMatchStatus(BankTransaction.MatchStatus.UNMATCHED);
			bankTransactionRepository.save(bank);
			return;
		}
		for (MatchSuggestionResponse suggestion : suggestions) {
			suggestionService.persistSuggestion(bank, suggestion);
		}
		bank.setMatchStatus(BankTransaction.MatchStatus.SUGGESTED);
		bankTransactionRepository.save(bank);
	}

	private BankTransactionResponse toResponse(BankTransaction txn) {
		List<MatchSuggestionResponse> suggestions;
		if (txn.getMatchStatus() == BankTransaction.MatchStatus.MATCHED
				|| txn.getMatchStatus() == BankTransaction.MatchStatus.IGNORED) {
			suggestions = List.of();
		} else {
			suggestions = suggestionService.suggest(txn);
		}
		return BankTransactionResponse.from(txn, suggestions);
	}

	private void rejectOpenSuggestions(BankTransaction bank) {
		for (ReconciliationMatch match : matchRepository.findActiveSuggestions(bank.getId())) {
			match.setStatus(ReconciliationMatch.MatchStatus.REJECTED);
			matchRepository.save(match);
		}
	}

	private BankTransaction requireBank(UUID clientId, UUID id) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return bankTransactionRepository.findByIdAndClient_IdAndFirmId(id, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Bank transaction", id));
	}

	private BankTransaction requireBankForUpdate(UUID clientId, UUID id) {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		return bankTransactionRepository.findByIdAndClient_IdAndFirmIdForUpdate(id, clientId, firmId)
				.orElseThrow(() -> new ResourceNotFoundException("Bank transaction", id));
	}

	private void assertEligibleForLedgerGeneration(BankTransaction bank) {
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.MATCHED) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.IGNORED) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
					"Ignored bank transactions cannot generate ledger entries");
		}
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.PENDING_APPROVAL) {
			throw alreadyConvertedFromBankState(bank);
		}
	}

	private void assertNoLedgerGeneration(UUID bankTransactionId) {
		ledgerGenerationRepository.findFetchedByBankTransaction_Id(bankTransactionId).ifPresent(existing -> {
			throw alreadyConverted(existing);
		});
	}

	private void persistLedgerGeneration(
			BankTransaction bank,
			BankTransactionLedgerGeneration.LedgerKind kind,
			UUID expenseId,
			UUID incomeId
	) {
		Expense expense = null;
		Income income = null;
		UUID firmId = bank.getFirmId();
		if (kind == BankTransactionLedgerGeneration.LedgerKind.EXPENSE) {
			expense = expenseRepository.findByIdAndClient_IdAndFirmId(expenseId, bank.getClient().getId(), firmId)
					.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
		} else {
			income = incomeRepository.findByIdAndClient_IdAndFirmId(incomeId, bank.getClient().getId(), firmId)
					.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
		}
		BankTransactionLedgerGeneration row = BankTransactionLedgerGeneration.builder()
				.client(bank.getClient())
				.bankTransaction(bank)
				.ledgerKind(kind)
				.expense(expense)
				.income(income)
				.build();
		row.setFirmId(firmId);
		try {
			ledgerGenerationRepository.saveAndFlush(row);
		} catch (DataIntegrityViolationException ex) {
			ledgerGenerationRepository.findFetchedByBankTransaction_Id(bank.getId())
					.ifPresentOrElse(
							generation -> {
								throw alreadyConverted(generation);
							},
							() -> {
								throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
										"This bank transaction already generated a ledger entry");
							});
		}
	}

	private BusinessException alreadyConverted(BankTransactionLedgerGeneration generation) {
		String entityType = generation.getLedgerKind().name();
		UUID entityId = generation.getLedgerKind() == BankTransactionLedgerGeneration.LedgerKind.EXPENSE
				? generation.getExpense().getId()
				: generation.getIncome().getId();
		return new BusinessException(
				ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
				"This bank transaction already generated a ledger entry",
				Map.of(
						"bankTransactionId", generation.getBankTransaction().getId(),
						"existingEntityType", entityType,
						"existingEntityId", entityId));
	}

	private BusinessException alreadyConvertedFromBankState(BankTransaction bank) {
		if (bank.getPendingExpenseId() != null) {
			return new BusinessException(
					ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
					"This bank transaction already generated a ledger entry",
					Map.of(
							"bankTransactionId", bank.getId(),
							"existingEntityType", "EXPENSE",
							"existingEntityId", bank.getPendingExpenseId()));
		}
		if (bank.getPendingIncomeId() != null) {
			return new BusinessException(
					ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
					"This bank transaction already generated a ledger entry",
					Map.of(
							"bankTransactionId", bank.getId(),
							"existingEntityType", "INCOME",
							"existingEntityId", bank.getPendingIncomeId()));
		}
		return new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
				"This bank transaction already generated a ledger entry",
				Map.of("bankTransactionId", bank.getId()));
	}

	private void assertReconciliationWritable(UUID clientId, LocalDate txnDate) {
		periodCloseService.assertPeriodOpen(clientId, txnDate);
	}

	private void assertBankAvailableForLedgerMatch(BankTransaction bank, UUID expenseId, UUID incomeId) {
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.MATCHED) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.IGNORED) {
			throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Ignored bank transactions cannot be reconciled");
		}
		assertNoCrossPathBankConsumption(bank);
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.PENDING_APPROVAL) {
			if (expenseId != null && !expenseId.equals(bank.getPendingExpenseId())) {
				throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT,
						"Bank transaction is pending approval for a different ledger entry");
			}
			if (incomeId != null && !incomeId.equals(bank.getPendingIncomeId())) {
				throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT,
						"Bank transaction is pending approval for a different ledger entry");
			}
		}
		ledgerGenerationRepository.findFetchedByBankTransaction_Id(bank.getId()).ifPresent(generation -> {
			if (expenseId != null
					&& generation.getLedgerKind() == BankTransactionLedgerGeneration.LedgerKind.EXPENSE
					&& generation.getExpense() != null
					&& expenseId.equals(generation.getExpense().getId())) {
				return;
			}
			if (incomeId != null
					&& generation.getLedgerKind() == BankTransactionLedgerGeneration.LedgerKind.INCOME
					&& generation.getIncome() != null
					&& incomeId.equals(generation.getIncome().getId())) {
				return;
			}
			throw alreadyConverted(generation);
		});
	}

	private void assertBankAvailableForInvoicePayment(BankTransaction bank) {
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.IGNORED) {
			throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Ignored bank transactions cannot be reconciled");
		}
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.PENDING_APPROVAL) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_CONVERTED,
					"This bank transaction already generated a ledger entry");
		}
		assertNoCrossPathBankConsumption(bank);
		ledgerGenerationRepository.findFetchedByBankTransaction_Id(bank.getId()).ifPresent(this::alreadyConvertedAndThrow);
	}

	private void assertNoCrossPathBankConsumption(BankTransaction bank) {
		UUID firmId = bank.getFirmId();
		if (matchRepository.existsByBankTransaction_IdAndStatus(
				bank.getId(), ReconciliationMatch.MatchStatus.CONFIRMED)) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		if (arPaymentRepository.existsByFirmIdAndBankTransactionIdAndStatusNot(
				firmId, bank.getId(), ArPayment.Status.REVERSED)) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		if (matchGroupItemRepository.existsByBankTransaction_IdAndBankClaimActiveTrue(bank.getId())) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
	}

	private UUID reverseActiveInvoicePaymentForBank(UUID clientId, UUID firmId, UUID bankTransactionId) {
		return arPaymentRepository.findByFirmIdAndBankTransactionIdAndStatusNot(
						firmId, bankTransactionId, ArPayment.Status.REVERSED)
				.map(payment -> {
					periodCloseService.assertPeriodOpen(clientId, payment.getPaymentDate());
					arPaymentService.reverseFromBankUnmatch(payment.getId(), new ReversePaymentRequest("Unmatched from bank reconciliation"));
					return payment.getId();
				})
				.orElse(null);
	}

	private void releaseActiveInvoiceMatchGroup(UUID firmId, UUID bankTransactionId) {
		matchGroupItemRepository.findActiveBankClaim(bankTransactionId, firmId).ifPresent(item -> {
			item.setBankClaimActive(false);
			matchGroupItemRepository.save(item);
			ReconciliationMatchGroup group = item.getGroup();
			if (group.getStatus() == ReconciliationMatchGroup.Status.CONFIRMED) {
				group.setStatus(ReconciliationMatchGroup.Status.REJECTED);
				group.setNotes("Unmatched by user");
				matchGroupRepository.save(group);
			}
		});
	}

	private void alreadyConvertedAndThrow(BankTransactionLedgerGeneration generation) {
		throw alreadyConverted(generation);
	}

	private void persistConfirmedMatch(ReconciliationMatch match) {
		try {
			matchRepository.saveAndFlush(match);
		} catch (DataIntegrityViolationException ex) {
			throw translateReconciliationUniqueViolation(ex);
		}
	}

	private BusinessException translateReconciliationUniqueViolation(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
				? ex.getMostSpecificCause().getMessage().toLowerCase()
				: "";
		if (message.contains("uq_recon_confirmed_bank_txn")
				|| message.contains("uq_recon_group_item_bank_txn")
				|| message.contains("uq_recon_group_item_bank_txn_active")) {
			return new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		if (message.contains("uq_recon_confirmed_expense")) {
			return new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Expense is already reconciled");
		}
		if (message.contains("uq_recon_confirmed_income")) {
			return new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Income is already reconciled");
		}
		if (message.contains("uq_ar_payments_bank_txn_active")) {
			return new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		return new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Reconciliation conflict");
	}

	private static void validateDirection(BankTransaction bank, boolean expense) {
		if (expense && !bank.isDebit()) {
			throw new BusinessException(ErrorCodes.INVALID_RECONCILIATION_DIRECTION,
					"Debit bank transactions can only be matched to expenses");
		}
		if (!expense && bank.isDebit()) {
			throw new BusinessException(ErrorCodes.INVALID_RECONCILIATION_DIRECTION,
					"Credit bank transactions can only be matched to income");
		}
	}

	private static void validateLedgerMatch(TransactionStatus status, BigDecimal ledgerAmount, BigDecimal bankAmount) {
		if (status != TransactionStatus.APPROVED) {
			throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Only approved transactions can be reconciled");
		}
		if (ledgerAmount == null || ledgerAmount.compareTo(bankAmount) != 0) {
			throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Amounts do not match");
		}
	}

	private static BankTransaction toEntity(Client client, BankAccount account, BankImport batch, ParsedBankRow row, String hash) {
		BankTransaction.TransactionDirection direction = row.credit() != null && row.credit().signum() > 0
				? BankTransaction.TransactionDirection.CREDIT
				: BankTransaction.TransactionDirection.DEBIT;
		BankTransaction txn = BankTransaction.builder()
				.client(client)
				.bankAccount(account)
				.bankImport(batch)
				.txnDate(row.txnDate())
				.valueDate(row.valueDate())
				.description(row.description())
				.referenceNo(row.referenceNo())
				.debit(row.debit())
				.credit(row.credit())
				.balance(row.balance())
				.direction(direction)
				.currency(account.getCurrency())
				.externalRowHash(hash)
				.matchStatus(BankTransaction.MatchStatus.UNMATCHED)
				.build();
		txn.setFirmId(client.getFirmId());
		return txn;
	}

	private void saveProfile(Client client, BankAccount account, CsvColumnMapping mapping, String profileName) {
		BankImportProfile profile = BankImportProfile.builder()
				.client(client)
				.bankAccount(account)
				.profileName(profileName)
				.dateColumn(mapping.dateColumn())
				.descriptionColumn(mapping.descriptionColumn())
				.referenceColumn(mapping.referenceColumn())
				.debitColumn(mapping.debitColumn())
				.creditColumn(mapping.creditColumn())
				.balanceColumn(mapping.balanceColumn())
				.amountColumn(mapping.amountColumn())
				.dateFormat(mapping.dateFormat())
				.headerRow(mapping.headerRow())
				.build();
		profile.setFirmId(client.getFirmId());
		profileRepository.save(profile);
	}

	private static String sha256(byte[] content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content));
		} catch (Exception ex) {
			throw new ValidationException("file", "Unable to checksum import file");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String defaultParty(String description, String fallback) {
		if (description == null || description.isBlank()) {
			return fallback;
		}
		return description.length() > 80 ? description.substring(0, 80) : description;
	}

	private static Receipt.DocumentType parseDocumentType(String raw) {
		if (raw == null || raw.isBlank()) {
			return Receipt.DocumentType.RECEIPT;
		}
		try {
			return Receipt.DocumentType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return Receipt.DocumentType.RECEIPT;
		}
	}
}
