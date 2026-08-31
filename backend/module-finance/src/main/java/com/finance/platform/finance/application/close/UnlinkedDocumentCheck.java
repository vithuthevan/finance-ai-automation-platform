package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(30)
@RequiredArgsConstructor
public class UnlinkedDocumentCheck implements CloseCheck {

	private final CloseReadinessQueryRepository queries;

	@Override
	public String code() {
		return "UNLINKED_DOCUMENTS_RESOLVED";
	}

	@Override
	public String label() {
		return "Financial documents are linked or dismissed";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.BLOCKER;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long unlinked = queries.countUnlinkedFinancial(context.firmId(), context.clientId(), context.from(), context.to());
		if (unlinked <= 0) {
			return List.of();
		}
		return List.of(CloseFinding.blocker(
				"UNLINKED_DOCUMENTS",
				unlinked == 1
						? "1 financial document is still unlinked."
						: unlinked + " financial documents are still unlinked.",
				(int) unlinked,
				"DOCUMENTS_UNLINKED"));
	}
}
