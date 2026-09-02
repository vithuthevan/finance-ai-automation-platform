package com.finance.platform.finance.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkItemResponse(
		String type,
		String priority,
		UUID clientId,
		String clientName,
		String title,
		String description,
		UUID resourceId,
		String actionUrl,
		LocalDate dueDate,
		boolean overdue,
		Instant createdAt,
		UUID assignedUserId,
		String assignedUserName
) {
}
