package com.finance.platform.finance.application.close;

import com.finance.platform.finance.infrastructure.persistence.CloseReadinessQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(20)
@RequiredArgsConstructor
public class DocumentReviewCheck implements CloseCheck {

	private final CloseReadinessQueryRepository queries;

	@Override
	public String code() {
		return "DOCUMENTS_REVIEWED";
	}

	@Override
	public String label() {
		return "Period documents have been reviewed";
	}

	@Override
	public CloseCheckSeverity defaultSeverity() {
		return CloseCheckSeverity.BLOCKER;
	}

	@Override
	public List<CloseFinding> evaluate(CloseCheckContext context) {
		long review = queries.countDocumentsNeedingReview(context.firmId(), context.clientId(), context.from(), context.to());
		long failedUnlinked = queries.countFailedUnlinked(context.firmId(), context.clientId(), context.from(), context.to());
		List<CloseFinding> findings = new ArrayList<>();
		if (review > 0) {
			findings.add(CloseFinding.blocker(
					"DOCUMENTS_NEED_REVIEW",
					review == 1 ? "1 document needs review." : review + " documents need review.",
					(int) review,
					"DOCUMENTS_REVIEW"));
		}
		if (failedUnlinked > 0) {
			findings.add(CloseFinding.blocker(
					"DOCUMENTS_FAILED",
					failedUnlinked == 1
							? "1 failed document still requires manual action."
							: failedUnlinked + " failed documents still require manual action.",
					(int) failedUnlinked,
					"DOCUMENTS_FAILED"));
		}
		return findings;
	}
}
