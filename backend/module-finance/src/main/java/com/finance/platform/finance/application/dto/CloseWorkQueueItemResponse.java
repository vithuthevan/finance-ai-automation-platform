package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.AccountingPeriod;

import java.time.LocalDate;
import java.util.UUID;

public record CloseWorkQueueItemResponse(
		UUID clientId,
		String clientName,
		UUID periodId,
		int year,
		int month,
		LocalDate startDate,
		LocalDate endDate,
		AccountingPeriod.PeriodStatus status,
		String displayStatus,
		boolean ready,
		int readinessPercent,
		int blockerCount
) {
}
