package com.finance.platform.finance.application.service;

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
import com.finance.platform.finance.application.bankimport.CsvColumnMapping;
import com.finance.platform.finance.application.bankimport.ImportParseResult;
import com.finance.platform.finance.application.bankimport.ImportPreview;
import com.finance.platform.finance.application.bankimport.ParsedBankRow;
import com.finance.platform.finance.application.dto.BankImportPreviewResponse;
import com.finance.platform.finance.application.dto.BankImportResponse;
import com.finance.platform.finance.application.dto.BankTransactionResponse;
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
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.BankImportJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankImportProfileJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.application.workflow.PeriodReadinessNotifier;
import com.finance.platform.finance.application.workflow.WorkflowNotificationService;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final PeriodReadinessNotifier periodReadinessNotifier;

	@Transactional(readOnly = true)
	public BankImportPreviewResponse previewImport(UUID clientId, UUID bankAccountId, byte[] content, CsvColumnMapping mapping) {
		clientAccessService.requireWriteAccess(clientId);
		bankAccountService.requireAccount(clientId, bankAccountId);
		ImportPreview preview = bankStatementImporter.preview(content, mapping);
		return BankImportPreviewResponse.from(preview);
	}

	@Transactional
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
		importRepository.findByBankAccount_IdAndChecksum(bankAccountId, checksum).ifPresent(existing -> {
			throw new BusinessException(ErrorCodes.BANK_IMPORT_DUPLICATE,
					"This statement file was already imported for this bank account");
		});
		if (profileName != null && !profileName.isBlank()) {
			saveProfile(client, account, mapping, profileName.trim());
		}
		ImportParseResult parsed = bankStatementImporter.parse(content, mapping);
		if (parsed.validRows().isEmpty()) {
			throw new BusinessException(ErrorCodes.BANK_IMPORT_INVALID, "No valid rows found in the statement");
		}
		String storageKey = "firms/" + client.getFirmId() + "/clients/" + client.getId() + "/bank/" + UUID.randomUUID() + ".csv";
		fileStorageService.store(storageKey, content, "text/csv");

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

		BankImport batch = BankImport.builder()
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
		batch = importRepository.save(batch);

		int imported = 0;
		int duplicates = 0;
		int failed = parsed.errors().size();
		for (ParsedBankRow row : parsed.validRows()) {
			String rowHash = row.rowHash();
			if (bankTransactionRepository.existsByBankAccount_IdAndExternalRowHash(bankAccountId, rowHash)) {
				duplicates++;
				continue;
			}
			BankTransaction txn = toEntity(client, account, batch, row, rowHash);
			bankTransactionRepository.save(txn);
			generateSuggestions(txn);
			imported++;
		}
		batch.setRowCount(parsed.validRows().size() + failed);
		batch.setImportedCount(imported);
		batch.setDuplicateCount(duplicates);
		batch.setFailedCount(failed);
		batch.setCompletedAt(Instant.now());
		BankImport saved = importRepository.save(batch);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.BANK_IMPORT_COMPLETED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of(
						"imported", imported,
						"duplicates", duplicates,
						"failed", failed))
				.build());
		workflowNotificationService.bankImportCompleted(client.getFirmId(), clientId, saved.getId());
		periodReadinessNotifier.checkAndNotify(client.getFirmId(), clientId);
		return BankImportResponse.from(saved);
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
		Page<BankTransaction> results = bankTransactionRepository.search(
				clientId,
				bankAccountId,
				status,
				from,
				to,
				blankToNull(query),
				PageRequest.of(page, capped, Sort.by(Sort.Direction.DESC, "txnDate")));
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
		long actionable = rows.size() - ignored;
		int percent = actionable == 0 ? 100 : (int) Math.round((matched + ignored) * 100.0 / actionable);
		return new ReconciliationSummaryResponse(
				rows.size(), matched, suggested, unmatched, ignored, pending,
				debits, credits, matchedValue, unmatchedValue, percent);
	}

	@Transactional
	public BankTransactionResponse confirmMatch(UUID clientId, UUID bankTransactionId, UUID expenseId, UUID incomeId) {
		clientAccessService.requireApproveAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		if (bank.getMatchStatus() == BankTransaction.MatchStatus.MATCHED) {
			throw new BusinessException(ErrorCodes.BANK_TRANSACTION_ALREADY_MATCHED, "Bank transaction is already matched");
		}
		ReconciliationMatch match = ReconciliationMatch.builder()
				.client(bank.getClient())
				.bankTransaction(bank)
				.status(ReconciliationMatch.MatchStatus.CONFIRMED)
				.confirmedBy(clientAccessService.requireCurrentUserEntity())
				.confirmedAt(Instant.now())
				.build();
		match.setFirmId(bank.getFirmId());
		if (expenseId != null) {
			Expense expense = expenseRepository.findByIdAndClientId(expenseId, clientId)
					.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
			validateDirection(bank, true);
			validateLedgerMatch(expense.getStatus(), expense.getAmount(), bank.absoluteAmount());
			if (matchRepository.isLedgerEntryMatched(expense.getId(), null)) {
				throw new BusinessException(ErrorCodes.RECONCILIATION_CONFLICT, "Expense is already reconciled");
			}
			match.setExpense(expense);
		} else if (incomeId != null) {
			Income income = incomeRepository.findByIdAndClientId(incomeId, clientId)
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
		matchRepository.save(match);
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
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		for (ReconciliationMatch match : matchRepository.findByBankTransaction_Id(bankTransactionId)) {
			if (match.getStatus() == ReconciliationMatch.MatchStatus.CONFIRMED) {
				match.setStatus(ReconciliationMatch.MatchStatus.REJECTED);
				match.setNotes("Unmatched by user");
				matchRepository.save(match);
			}
		}
		bank.setMatchStatus(BankTransaction.MatchStatus.UNMATCHED);
		BankTransaction saved = bankTransactionRepository.save(bank);
		generateSuggestions(saved);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_REMOVED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.build());
		return toResponse(saved);
	}

	@Transactional
	public BankTransactionResponse ignore(UUID clientId, UUID bankTransactionId, String reason) {
		clientAccessService.requireApproveAccess(clientId);
		if (reason == null || reason.isBlank()) {
			throw new ValidationException("reason", "An ignore reason is required");
		}
		BankTransaction bank = requireBank(clientId, bankTransactionId);
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
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		validateDirection(bank, true);
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
		bank.setPendingExpenseId(created.id());
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
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		assertReconciliationWritable(clientId, bank.getTxnDate());
		validateDirection(bank, false);
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
		bank.setPendingIncomeId(created.id());
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
		return toResponse(bankTransactionRepository.findById(bankTransactionId).orElse(bank));
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
		List<MatchSuggestionResponse> suggestions = matchRepository.findActiveSuggestions(txn.getId()).stream()
				.map(match -> new MatchSuggestionResponse(
						match.getId(),
						match.getExpense() != null ? "EXPENSE" : "INCOME",
						match.getExpense() != null ? match.getExpense().getId() : match.getIncome().getId(),
						match.getExpense() != null
								? match.getExpense().getVendorName()
								: match.getIncome().getCustomerName(),
						match.getExpense() != null ? match.getExpense().getAmount() : match.getIncome().getAmount(),
						match.getExpense() != null
								? match.getExpense().getTransactionDate()
								: match.getIncome().getTransactionDate(),
						match.getExpense() != null
								? match.getExpense().getDescription()
								: match.getIncome().getDescription(),
						match.getMatchScore() == null ? 0 : match.getMatchScore(),
						match.getConfidence(),
						match.getStatus()))
				.toList();
		return BankTransactionResponse.from(txn, suggestions);
	}

	private void rejectOpenSuggestions(BankTransaction bank) {
		for (ReconciliationMatch match : matchRepository.findActiveSuggestions(bank.getId())) {
			match.setStatus(ReconciliationMatch.MatchStatus.REJECTED);
			matchRepository.save(match);
		}
	}

	private BankTransaction requireBank(UUID clientId, UUID id) {
		return bankTransactionRepository.findByIdAndClient_Id(id, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Bank transaction", id));
	}

	private void assertReconciliationWritable(UUID clientId, LocalDate txnDate) {
		periodCloseService.assertPeriodOpen(clientId, txnDate);
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
		return BankTransaction.builder()
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
