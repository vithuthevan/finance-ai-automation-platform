package com.finance.platform.finance.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MonthEndClientRowResponse(
		UUID clientId,
		String clientName,
		UUID periodId,
		int year,
		int month,
		LocalDate periodStart,
		LocalDate periodEnd,
		MonthEndPortfolioState state,
		String stateLabel,
		boolean readyToClose,
		int readinessPercent,
		long overdueDocumentRequests,
		int waitingOnClientItems,
		int teamActionItems,
		UUID primaryAccountantUserId,
		String primaryAccountantName,
		List<MonthEndProgressStepResponse> progress,
		List<MonthEndBlockerResponse> blockers,
		CloseActionLinkResponse primaryAction
) {
}
