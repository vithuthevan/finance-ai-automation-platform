package com.finance.platform.finance.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PracticeWorkAssignmentResponse(
		UUID id,
		UUID clientId,
		String sourceType,
		UUID sourceId,
		UUID assignedUserId,
		String status,
		LocalDate dueDate
) {
}
