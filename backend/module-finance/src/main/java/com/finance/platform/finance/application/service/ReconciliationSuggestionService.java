package com.finance.platform.finance.application.service;

import com.finance.platform.finance.application.dto.MatchSuggestionResponse;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.ReconciliationMatch;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReconciliationMatchJpaRepository;
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

/**
 * Deterministic reconciliation scoring (no AI).
 * <p>
 * Score factors (documented):
 * <ul>
 *   <li>Exact amount match: +50</li>
 *   <li>Same calendar day: +25</li>
 *   <li>Within 3 days: +15</li>
 *   <li>Reference token overlap: up to +15</li>
 *   <li>Description/vendor overlap: up to +10</li>
 * </ul>
 * Confidence: HIGH &gt;= 70, MEDIUM &gt;= 45, LOW otherwise.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationSuggestionService {

	private static final int TOP_SUGGESTIONS = 3;

	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final ReconciliationMatchJpaRepository matchRepository;

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
				int score = score(bank, amount, expense.getAmount(), expense.getTransactionDate(),
						expense.getReferenceNo(), expense.getVendorName(), expense.getDescription());
				if (score > 0) {
					candidates.add(new ScoredCandidate("EXPENSE", expense.getId(),
							label(expense.getVendorName(), expense.getDescription()),
							expense.getAmount(), expense.getTransactionDate(), expense.getDescription(), score));
				}
			}
		} else {
			for (Income income : incomeRepository.findByClientIdAndStatusAndTransactionDateBetween(
					clientId, TransactionStatus.APPROVED, from, to)) {
				if (matchRepository.isLedgerEntryMatched(null, income.getId())) {
					continue;
				}
				int score = score(bank, amount, income.getAmount(), income.getTransactionDate(),
						income.getReferenceNo(), income.getCustomerName(), income.getDescription());
				if (score > 0) {
					candidates.add(new ScoredCandidate("INCOME", income.getId(),
							label(income.getCustomerName(), income.getDescription()),
							income.getAmount(), income.getTransactionDate(), income.getDescription(), score));
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
						ReconciliationMatch.MatchStatus.SUGGESTED))
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

	private static int score(
			BankTransaction bank,
			BigDecimal bankAmount,
			BigDecimal ledgerAmount,
			LocalDate ledgerDate,
			String ledgerReference,
			String ledgerParty,
			String ledgerDescription
	) {
		if (ledgerAmount == null || ledgerAmount.compareTo(bankAmount) != 0) {
			return 0;
		}
		int score = 50;
		long dayDiff = Math.abs(ChronoUnit.DAYS.between(bank.getTxnDate(), ledgerDate));
		if (dayDiff == 0) {
			score += 25;
		} else if (dayDiff <= 3) {
			score += 15;
		}
		score += referenceOverlap(bank.getReferenceNo(), ledgerReference);
		score += textOverlap(bank.getDescription(), ledgerParty, ledgerDescription);
		return score;
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

	private record ScoredCandidate(
			String ledgerType,
			UUID ledgerId,
			String ledgerLabel,
			BigDecimal amount,
			LocalDate transactionDate,
			String description,
			int score
	) {
	}
}
