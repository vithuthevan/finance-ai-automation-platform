package com.finance.platform.finance.application.service;

import com.finance.platform.finance.application.dto.MatchScoreComponentResponse;
import com.finance.platform.finance.application.dto.MatchSuggestionResponse;
import com.finance.platform.finance.application.service.invoicing.InvoiceSettlementService;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.domain.model.invoicing.ArCustomer;
import com.finance.platform.finance.domain.model.invoicing.SalesInvoice;
import com.finance.platform.finance.infrastructure.persistence.ArCustomerJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.SalesInvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReconciliationSuggestionService {

	private static final int TOP_SUGGESTIONS = 3;

	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final ReconciliationMatchJpaRepository matchRepository;
	private final SalesInvoiceJpaRepository salesInvoiceRepository;
	private final ArCustomerJpaRepository arCustomerRepository;
	private final InvoiceSettlementService invoiceSettlementService;

	public List<MatchSuggestionResponse> suggest(BankTransaction bank) {
		UUID clientId = bank.getClient().getId();
		BigDecimal amount = bank.absoluteAmount();
		if (amount.signum() == 0) {
			return List.of();
		}
		LocalDate from = bank.getTxnDate().minusDays(7);
		LocalDate to = bank.getTxnDate().plusDays(7);
		List<ScoredCandidate> candidates = new ArrayList<>();
		if (bank.isDebit()) {
			for (Expense expense : expenseRepository.findByClientIdAndStatusAndTransactionDateBetween(
					clientId, TransactionStatus.APPROVED, from, to)) {
				if (matchRepository.isLedgerEntryMatched(expense.getId(), null)) {
					continue;
				}
				ScoreBreakdown breakdown = scoreLedger(
						bank, amount, expense.getAmount(), expense.getTransactionDate(),
						expense.getReferenceNo(), expense.getVendorName(), expense.getDescription());
				if (breakdown.score() > 0) {
					candidates.add(new ScoredCandidate("EXPENSE", expense.getId(),
							label(expense.getVendorName(), expense.getDescription()),
							expense.getAmount(), expense.getTransactionDate(), expense.getDescription(),
							breakdown.score(), breakdown.components()));
				}
			}
		} else {
			for (ArCustomer customer : arCustomerRepository.findByFirmIdAndActiveTrueOrderByNameAsc(bank.getFirmId())) {
				if (customer.getClientId() == null || !customer.getClientId().equals(clientId)) {
					continue;
				}
				for (SalesInvoice invoice : salesInvoiceRepository.findByFirmIdOrderByCreatedAtDesc(bank.getFirmId())) {
					if (!invoice.getCustomerId().equals(customer.getId())
							|| invoice.getStatus() != SalesInvoice.DocumentStatus.ISSUED) {
						continue;
					}
					BigDecimal outstanding = invoiceSettlementService.outstanding(invoice);
					if (outstanding.signum() <= 0) {
						continue;
					}
					ScoreBreakdown breakdown = scoreInvoice(bank, amount, outstanding, invoice);
					if (breakdown.score() > 0) {
						candidates.add(new ScoredCandidate("INVOICE", invoice.getId(),
								invoice.getInvoiceNumber(),
								outstanding,
								invoice.getIssueDate(),
								"Sales invoice " + invoice.getInvoiceNumber(),
								breakdown.score(), breakdown.components()));
					}
				}
			}
			for (Income income : incomeRepository.findByClientIdAndStatusAndTransactionDateBetween(
					clientId, TransactionStatus.APPROVED, from, to)) {
				if (matchRepository.isLedgerEntryMatched(null, income.getId())) {
					continue;
				}
				ScoreBreakdown breakdown = scoreLedger(
						bank, amount, income.getAmount(), income.getTransactionDate(),
						income.getReferenceNo(), income.getCustomerName(), income.getDescription());
				if (breakdown.score() > 0) {
					candidates.add(new ScoredCandidate("INCOME", income.getId(),
							label(income.getCustomerName(), income.getDescription()),
							income.getAmount(), income.getTransactionDate(), income.getDescription(),
							breakdown.score(), breakdown.components()));
				}
			}
		}
		return candidates.stream()
				.sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
				.limit(TOP_SUGGESTIONS)
				.map(candidate -> new MatchSuggestionResponse(
						null,
						candidate.ledgerType(),
						candidate.ledgerId(),
						candidate.ledgerLabel(),
						candidate.amount(),
						candidate.transactionDate(),
						candidate.description(),
						candidate.score(),
						confidence(candidate.score()),
						ReconciliationMatch.MatchStatus.SUGGESTED,
						candidate.components()))
				.toList();
	}

	public ReconciliationMatch persistSuggestion(BankTransaction bank, MatchSuggestionResponse suggestion) {
		ReconciliationMatch match = ReconciliationMatch.builder()
				.client(bank.getClient())
				.bankTransaction(bank)
				.status(ReconciliationMatch.MatchStatus.SUGGESTED)
				.matchScore(suggestion.score())
				.confidence(suggestion.confidence())
				.build();
		match.setFirmId(bank.getFirmId());
		UUID clientId = bank.getClient().getId();
		UUID firmId = bank.getFirmId();
		if ("EXPENSE".equals(suggestion.ledgerType())) {
			match.setExpense(expenseRepository.findByIdAndClient_IdAndFirmId(suggestion.ledgerId(), clientId, firmId)
					.orElse(null));
		} else if ("INCOME".equals(suggestion.ledgerType())) {
			match.setIncome(incomeRepository.findByIdAndClient_IdAndFirmId(suggestion.ledgerId(), clientId, firmId)
					.orElse(null));
		}
		return matchRepository.save(match);
	}

	private static ScoreBreakdown scoreInvoice(
			BankTransaction bank,
			BigDecimal bankAmount,
			BigDecimal outstanding,
			SalesInvoice invoice
	) {
		List<MatchScoreComponentResponse> components = new ArrayList<>();
		if (outstanding.compareTo(bankAmount) != 0) {
			return new ScoreBreakdown(0, List.of());
		}
		components.add(new MatchScoreComponentResponse("Exact amount", 50));
		int score = 50;
		String ref = bank.getReferenceNo() == null ? "" : bank.getReferenceNo().toUpperCase(Locale.ROOT);
		String invoiceNo = invoice.getInvoiceNumber().toUpperCase(Locale.ROOT);
		if (!ref.isBlank() && ref.contains(invoiceNo)) {
			components.add(new MatchScoreComponentResponse("Invoice reference", 30));
			score += 30;
		}
		if (invoice.getIssueDate() != null) {
			long dayDiff = Math.abs(ChronoUnit.DAYS.between(bank.getTxnDate(), invoice.getIssueDate()));
			if (dayDiff <= 7) {
				components.add(new MatchScoreComponentResponse("Date proximity", 8));
				score += 8;
			}
		}
		return new ScoreBreakdown(score, components);
	}

	private static ScoreBreakdown scoreLedger(
			BankTransaction bank,
			BigDecimal bankAmount,
			BigDecimal ledgerAmount,
			LocalDate ledgerDate,
			String ledgerReference,
			String ledgerParty,
			String ledgerDescription
	) {
		if (ledgerAmount == null || ledgerAmount.compareTo(bankAmount) != 0) {
			return new ScoreBreakdown(0, List.of());
		}
		List<MatchScoreComponentResponse> components = new ArrayList<>();
		components.add(new MatchScoreComponentResponse("Exact amount", 50));
		int score = 50;
		long dayDiff = Math.abs(ChronoUnit.DAYS.between(bank.getTxnDate(), ledgerDate));
		if (dayDiff == 0) {
			components.add(new MatchScoreComponentResponse("Same calendar day", 25));
			score += 25;
		} else if (dayDiff <= 3) {
			components.add(new MatchScoreComponentResponse("Date within 3 days", 15));
			score += 15;
		}
		int refScore = referenceOverlap(bank.getReferenceNo(), ledgerReference);
		if (refScore > 0) {
			components.add(new MatchScoreComponentResponse("Reference overlap", refScore));
			score += refScore;
		}
		int textScore = textOverlap(bank.getDescription(), ledgerParty, ledgerDescription);
		if (textScore > 0) {
			components.add(new MatchScoreComponentResponse("Description match", textScore));
			score += textScore;
		}
		return new ScoreBreakdown(score, components);
	}

	private static int referenceOverlap(String bankRef, String ledgerRef) {
		if (bankRef == null || bankRef.isBlank() || ledgerRef == null || ledgerRef.isBlank()) {
			return 0;
		}
		String left = bankRef.trim().toLowerCase(Locale.ROOT);
		String right = ledgerRef.trim().toLowerCase(Locale.ROOT);
		if (left.equals(right)) {
			return 15;
		}
		if (left.contains(right) || right.contains(left)) {
			return 10;
		}
		return 0;
	}

	private static int textOverlap(String bankDescription, String party, String ledgerDescription) {
		String haystack = normalize(bankDescription);
		if (haystack.isBlank()) {
			return 0;
		}
		int score = 0;
		String partyNorm = normalize(party);
		if (!partyNorm.isBlank() && haystack.contains(partyNorm)) {
			score += 6;
		}
		String descNorm = normalize(ledgerDescription);
		if (!descNorm.isBlank() && haystack.contains(descNorm)) {
			score += 4;
		}
		return Math.min(score, 10);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String label(String party, String description) {
		if (party != null && !party.isBlank()) {
			return party;
		}
		return description == null ? "Ledger entry" : description;
	}

	private static String confidence(int score) {
		if (score >= 70) {
			return "HIGH";
		}
		if (score >= 45) {
			return "MEDIUM";
		}
		return "LOW";
	}

	private record ScoreBreakdown(int score, List<MatchScoreComponentResponse> components) {
	}

	private record ScoredCandidate(
			String ledgerType,
			UUID ledgerId,
			String ledgerLabel,
			BigDecimal amount,
			LocalDate transactionDate,
			String description,
			int score,
			List<MatchScoreComponentResponse> components
	) {
	}
}
