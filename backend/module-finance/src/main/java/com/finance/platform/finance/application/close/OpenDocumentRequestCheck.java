package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(40)
@RequiredArgsConstructor
public class OpenDocumentRequestCheck implements CloseCheck {

	private final CloseReadinessQueryRepository queries;

	@Override
	public String code() {
		return "MISSING_DOCUMENT_REQUESTS_RESOLVED";
	}

	@Override
	public String label() {
		return "Missing-document requests are completed";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.BLOCKER;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long open = queries.countOpenDocumentRequests(
				context.firmId(), context.clientId(), context.periodId(), context.from(), context.to());
		if (open <= 0) {
			return List.of();
		}
		return List.of(CloseFinding.blocker(
				"OPEN_DOCUMENT_REQUESTS",
				open == 1
						? "Waiting on client: 1 requested document outstanding."
						: "Waiting on client: " + open + " requested documents outstanding.",
				(int) open,
				"DOCUMENT_REQUESTS"));
	}
}
