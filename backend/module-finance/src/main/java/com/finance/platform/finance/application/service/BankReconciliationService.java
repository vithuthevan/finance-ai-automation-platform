package com.finance.platform.finance.application.service;

import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.core.storage.FileStorageService;
import com.finance.platform.finance.domain.model.BankImport;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.BankImportJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankReconciliationService {

	private final BankImportJpaRepository importRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;
	private final ReconciliationMatchJpaRepository matchRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final ClientAccessService clientAccessService;
	private final FileStorageService fileStorageService;
	private final AuditLogger auditLogger;

	@Transactional
	public BankImport importCsv(UUID clientId, String fileName, byte[] content, CsvMapping mapping) {
		Client client = clientAccessService.requireWriteAccess(clientId);
		if (content == null || content.length == 0) {
			throw new ValidationException("file", "CSV content is required");
		}
		String storageKey = client.getFirmId() + "/" + client.getId() + "/bank/" + UUID.randomUUID() + ".csv";
		fileStorageService.store(storageKey, content, "text/csv");
		BankImport batch = BankImport.builder()
				.client(client)
				.uploadedBy(clientAccessService.requireCurrentUserEntity())
				.fileName(fileName == null ? "statement.csv" : fileName)
				.storageKey(storageKey)
				.status("IMPORTED")
				.build();
		batch.setFirmId(client.getFirmId());
		batch = importRepository.save(batch);

		List<BankTransaction> rows = parseCsv(content, mapping);
		for (BankTransaction row : rows) {
			row.setClient(client);
			row.setBankImport(batch);
			row.setFirmId(client.getFirmId());
			bankTransactionRepository.save(row);
			suggestMatch(clientId, row);
		}
		batch.setRowCount(rows.size());
		BankImport saved = importRepository.save(batch);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(client.getFirmId())
				.action(AuditAction.BANK_IMPORTED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("rowCount", saved.getRowCount()))
				.build());
		return saved;
	}

	@Transactional(readOnly = true)
	public PageResponse<com.finance.platform.finance.application.dto.BankTransactionResponse> list(UUID clientId, BankTransaction.MatchStatus status, int page, int size) {
		clientAccessService.requireReadAccess(clientId);
		Page<BankTransaction> results = status == null
				? bankTransactionRepository.findByClientId(clientId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "txnDate")))
				: bankTransactionRepository.findByClientIdAndMatchStatus(clientId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "txnDate")));
		return new PageResponse<>(
				results.map(com.finance.platform.finance.application.dto.BankTransactionResponse::from).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	@Transactional(readOnly = true)
	public Map<String, Long> dashboard(UUID clientId) {
		clientAccessService.requireReadAccess(clientId);
		Map<String, Long> counts = new LinkedHashMap<>();
		for (BankTransaction.MatchStatus status : BankTransaction.MatchStatus.values()) {
			counts.put(status.name(), bankTransactionRepository.countByClientIdAndMatchStatus(clientId, status));
		}
		return counts;
	}

	@Transactional
	public BankTransaction confirmMatch(UUID clientId, UUID bankTransactionId, UUID expenseId, UUID incomeId) {
		clientAccessService.requireWriteAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		ReconciliationMatch match = ReconciliationMatch.builder()
				.client(bank.getClient())
				.bankTransaction(bank)
				.status(ReconciliationMatch.MatchStatus.CONFIRMED)
				.confirmedBy(clientAccessService.requireCurrentUserEntity())
				.confirmedAt(Instant.now())
				.build();
		if (expenseId != null) {
			Expense expense = expenseRepository.findByIdAndClientId(expenseId, clientId)
					.orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
			match.setExpense(expense);
		} else if (incomeId != null) {
			Income income = incomeRepository.findByIdAndClientId(incomeId, clientId)
					.orElseThrow(() -> new ResourceNotFoundException("Income", incomeId));
			match.setIncome(income);
		} else {
			throw new ValidationException("A matching expense or income is required");
		}
		match.setFirmId(bank.getFirmId());
		matchRepository.save(match);
		bank.setMatchStatus(BankTransaction.MatchStatus.MATCHED);
		BankTransaction saved = bankTransactionRepository.save(bank);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(bank.getFirmId())
				.action(AuditAction.RECONCILIATION_CONFIRMED)
				.resourceType(AuditResourceType.BANK)
				.resourceId(bank.getId())
				.clientId(clientId)
				.build());
		return saved;
	}

	@Transactional
	public BankTransaction markStatus(UUID clientId, UUID bankTransactionId, BankTransaction.MatchStatus status) {
		clientAccessService.requireWriteAccess(clientId);
		BankTransaction bank = requireBank(clientId, bankTransactionId);
		bank.setMatchStatus(status);
		return bankTransactionRepository.save(bank);
	}

	private void suggestMatch(UUID clientId, BankTransaction bank) {
		BigDecimal amount = bank.getCredit() != null && bank.getCredit().signum() > 0 ? bank.getCredit() : bank.getDebit();
		if (amount == null) {
			return;
		}
		if (bank.getCredit() != null && bank.getCredit().signum() > 0) {
			incomeRepository.findByClientIdAndStatusAndTransactionDateBetween(
							clientId, TransactionStatus.APPROVED, bank.getTxnDate().minusDays(3), bank.getTxnDate().plusDays(3))
					.stream()
					.filter(income -> income.getAmount().compareTo(amount) == 0)
					.findFirst()
					.ifPresent(income -> {
						bank.setMatchStatus(BankTransaction.MatchStatus.SUGGESTED);
						ReconciliationMatch match = ReconciliationMatch.builder()
								.client(bank.getClient())
								.bankTransaction(bank)
								.income(income)
								.status(ReconciliationMatch.MatchStatus.SUGGESTED)
								.build();
						match.setFirmId(bank.getFirmId());
						matchRepository.save(match);
					});
		} else {
			expenseRepository.findByClientIdAndStatusAndTransactionDateBetween(
							clientId, TransactionStatus.APPROVED, bank.getTxnDate().minusDays(3), bank.getTxnDate().plusDays(3))
					.stream()
					.filter(expense -> expense.getAmount().compareTo(amount) == 0)
					.findFirst()
					.ifPresent(expense -> {
						bank.setMatchStatus(BankTransaction.MatchStatus.SUGGESTED);
						ReconciliationMatch match = ReconciliationMatch.builder()
								.client(bank.getClient())
								.bankTransaction(bank)
								.expense(expense)
								.status(ReconciliationMatch.MatchStatus.SUGGESTED)
								.build();
						match.setFirmId(bank.getFirmId());
						matchRepository.save(match);
					});
		}
	}

	private BankTransaction requireBank(UUID clientId, UUID id) {
		return bankTransactionRepository.findByIdAndClientId(id, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("BankTransaction", id));
	}

	private List<BankTransaction> parseCsv(byte[] content, CsvMapping mapping) {
		CsvMapping cols = mapping == null ? CsvMapping.defaults() : mapping;
		String text = new String(content);
		String[] lines = text.split("\\r?\\n");
		List<BankTransaction> rows = new ArrayList<>();
		boolean headerSkipped = false;
		for (String line : lines) {
			if (line.isBlank()) {
				continue;
			}
			String[] parts = line.split(",", -1);
			if (!headerSkipped) {
				headerSkipped = true;
				continue;
			}
			if (parts.length <= Math.max(cols.date(), Math.max(cols.description(), Math.max(cols.debit(), cols.credit())))) {
				continue;
			}
			BankTransaction row = BankTransaction.builder()
					.txnDate(parseDate(parts[cols.date()]))
					.description(safe(parts, cols.description()))
					.referenceNo(safe(parts, cols.reference()))
					.debit(parseAmount(parts, cols.debit()))
					.credit(parseAmount(parts, cols.credit()))
					.balance(parseAmount(parts, cols.balance()))
					.matchStatus(BankTransaction.MatchStatus.UNMATCHED)
					.build();
			rows.add(row);
		}
		return rows;
	}

	private static LocalDate parseDate(String raw) {
		String value = raw == null ? "" : raw.trim().replace("\"", "");
		for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"))) {
			try {
				return LocalDate.parse(value, formatter);
			} catch (Exception ignored) {
			}
		}
		return LocalDate.now();
	}

	private static BigDecimal parseAmount(String[] parts, int index) {
		String value = safe(parts, index);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(value.replace(",", "").replace("\"", "").trim());
		} catch (Exception ex) {
			return null;
		}
	}

	private static String safe(String[] parts, int index) {
		if (index < 0 || index >= parts.length) {
			return null;
		}
		String value = parts[index].trim();
		return value.isEmpty() ? null : value.replace("\"", "");
	}

	public record CsvMapping(int date, int description, int reference, int debit, int credit, int balance) {
		public static CsvMapping defaults() {
			return new CsvMapping(0, 1, 2, 3, 4, 5);
		}
	}

	@SuppressWarnings("unused")
	private static long daysBetween(LocalDate left, LocalDate right) {
		return Math.abs(ChronoUnit.DAYS.between(left, right));
	}
}
