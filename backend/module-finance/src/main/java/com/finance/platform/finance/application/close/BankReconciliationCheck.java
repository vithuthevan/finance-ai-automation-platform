package com.finance.platform.finance.application.close;

import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.infrastructure.persistence.BankAccountJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Active when the client has at least one active bank account.
 * <ul>
 *   <li>No bank account configured → check skipped (not a blocker)</li>
 *   <li>Bank account but no imported transactions in period → WARNING</li>
 *   <li>Imported transactions with actionable unmatched rows → BLOCKER</li>
 * </ul>
 */
@Component
@Order(90)
@RequiredArgsConstructor
public class BankReconciliationCheck implements CloseCheck {

	private final BankAccountJpaRepository bankAccountRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;

	@Override
	public String code() {
		return "BANK_RECONCILIATION_COMPLETE";
	}

	@Override
	public String label() {
		return "Bank reconciliation is complete";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.BLOCKER;
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long accounts = bankAccountRepository.countByClient_IdAndActiveTrue(context.clientId());
		if (accounts == 0) {
			return List.of();
		}
		long unmatched = bankTransactionRepository.countByClientAndPeriodAndStatus(
				context.clientId(), context.from(), context.to(), BankTransaction.MatchStatus.UNMATCHED);
		long suggested = bankTransactionRepository.countByClientAndPeriodAndStatus(
				context.clientId(), context.from(), context.to(), BankTransaction.MatchStatus.SUGGESTED);
		long pending = bankTransactionRepository.countByClientAndPeriodAndStatus(
				context.clientId(), context.from(), context.to(), BankTransaction.MatchStatus.PENDING_APPROVAL);
		long actionable = unmatched + suggested + pending;
		if (actionable == 0) {
			return List.of();
		}
		List<CloseFinding> findings = new ArrayList<>();
		if (unmatched > 0) {
			findings.add(CloseFinding.blocker(
					"BANK_RECONCILIATION_INCOMPLETE",
					unmatched == 1 ? "1 unmatched bank transaction remains." : unmatched + " unmatched bank transactions remain.",
					(int) unmatched,
					"BANKING_UNMATCHED"));
		}
		if (suggested > 0) {
			findings.add(CloseFinding.blocker(
					"BANK_RECONCILIATION_SUGGESTED",
					suggested == 1 ? "1 bank transaction has an unreviewed suggestion." : suggested + " bank transactions have unreviewed suggestions.",
					(int) suggested,
					"BANKING_SUGGESTED"));
		}
		if (pending > 0) {
			findings.add(CloseFinding.blocker(
					"BANK_RECONCILIATION_PENDING_APPROVAL",
					pending == 1 ? "1 bank-created draft awaits approval before reconciliation can complete." : pending + " bank-created drafts await approval.",
					(int) pending,
					"BANKING_PENDING"));
		}
		return findings;
	}
}
