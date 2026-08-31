package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.AccountingPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PeriodResponse(
		UUID id,
		UUID clientId,
		String clientName,
		int year,
		int month,
		LocalDate startDate,
		LocalDate endDate,
		AccountingPeriod.PeriodStatus status,
		boolean ready,
		int readinessPercent,
		int blockerCount,
		List<CloseFindingResponse> blockers,
		Instant reviewStartedAt,
		UUID reviewStartedById,
		String reviewStartedByName,
		Instant closedAt,
		UUID closedById,
		String closedByName,
		String closeNote,
		Instant reopenedAt,
		UUID reopenedById,
		String reopenedByName,
		String reopenReason,
		Instant createdAt
) {
}
