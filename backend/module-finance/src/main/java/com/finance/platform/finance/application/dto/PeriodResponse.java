package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.AccountingPeriod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PeriodResponse(
		UUID id,
		UUID clientId,
		int year,
		int month,
		AccountingPeriod.PeriodStatus status,
		int readinessPercent,
		List<String> blockers,
		Instant closedAt,
		String reopenReason
) {
}
