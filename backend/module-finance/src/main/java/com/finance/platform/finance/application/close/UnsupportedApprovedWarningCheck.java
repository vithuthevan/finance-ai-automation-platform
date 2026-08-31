package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Approved transactions without supporting documents. V1 treats this as a WARNING:
 * some legitimate entries do not require a receipt. Later firm policy can raise this to BLOCKER.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class UnsupportedApprovedWarningCheck implements CloseCheck {

	private final CloseReadinessQueryRepository queries;

	@Override
	public String code() {
		return "UNSUPPORTED_APPROVED_TRANSACTIONS";
	}

	@Override
	public String label() {
		return "Approved transactions have supporting documents";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.WARNING;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long expenses = queries.countApprovedWithoutDocuments(
				"expenses", context.firmId(), context.clientId(), context.from(), context.to());
		long income = queries.countApprovedWithoutDocuments(
				"income", context.firmId(), context.clientId(), context.from(), context.to());
		long total = expenses + income;
		if (total <= 0) {
			return List.of();
		}
		return List.of(CloseFinding.warning(
				"UNSUPPORTED_APPROVED_TRANSACTIONS",
				total == 1
						? "1 approved transaction has no supporting document."
						: total + " approved transactions have no supporting document.",
				(int) total,
				"TRANSACTIONS_UNSUPPORTED"));
	}
}
