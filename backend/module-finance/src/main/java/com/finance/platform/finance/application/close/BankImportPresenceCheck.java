package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.BankAccountJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(89)
@RequiredArgsConstructor
public class BankImportPresenceCheck implements CloseCheck {

	private final BankAccountJpaRepository bankAccountRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;

	@Override
	public String code() {
		return "BANK_STATEMENT_IMPORTED";
	}

	@Override
	public String label() {
		return "Bank statement imported for period";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.WARNING;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long accounts = bankAccountRepository.countByClient_IdAndActiveTrue(context.clientId());
		if (accounts == 0) {
			return List.of();
		}
		long imported = bankTransactionRepository.countImportedInPeriod(
				context.clientId(), context.from(), context.to());
		if (imported > 0) {
			return List.of();
		}
		return List.of(CloseFinding.warning(
				"BANK_RECONCILIATION_NOT_STARTED",
				"No bank statement has been imported for this period.",
				1,
				"BANKING"));
	}
}
