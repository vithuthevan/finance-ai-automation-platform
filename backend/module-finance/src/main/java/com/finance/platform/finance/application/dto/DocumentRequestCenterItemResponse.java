package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentRequestCenterItemResponse(
		UUID id,
		UUID clientId,
		String clientName,
		String title,
		String description,
		Receipt.DocumentType documentType,
		LocalDate dueDate,
		DocumentRequest.RequestStatus status,
		boolean overdue,
		long ageDays,
		Instant createdAt,
		Instant lastReminderAt,
		int reminderCount,
		UUID periodId,
		Integer periodYear,
		Integer periodMonth,
		String nextAction,
		String actionPath
) {
}
