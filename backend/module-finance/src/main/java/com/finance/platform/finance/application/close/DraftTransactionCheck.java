package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(10)
@RequiredArgsConstructor
public class DraftTransactionCheck implements CloseCheck {

	private final CloseReadinessQueryRepository queries;

	@Override
	public String code() {
		return "TRANSACTIONS_APPROVED";
	}

	@Override
	public String label() {
		return "All period transactions are approved";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.BLOCKER;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long draftExpenses = queries.countByStatus(
				"expenses", context.firmId(), context.clientId(), "DRAFT", context.from(), context.to());
		long draftIncome = queries.countByStatus(
				"income", context.firmId(), context.clientId(), "DRAFT", context.from(), context.to());
		List<CloseFinding> findings = new ArrayList<>();
		if (draftExpenses > 0) {
			findings.add(CloseFinding.blocker(
					"DRAFT_EXPENSES",
					draftExpenses == 1 ? "1 draft expense remains." : draftExpenses + " draft expenses remain.",
					(int) draftExpenses,
					"EXPENSES_DRAFT"));
		}
		if (draftIncome > 0) {
			findings.add(CloseFinding.blocker(
					"DRAFT_INCOME",
					draftIncome == 1 ? "1 draft income remains." : draftIncome + " draft income records remain.",
					(int) draftIncome,
					"INCOME_DRAFT"));
		}
		return findings;
	}
}
