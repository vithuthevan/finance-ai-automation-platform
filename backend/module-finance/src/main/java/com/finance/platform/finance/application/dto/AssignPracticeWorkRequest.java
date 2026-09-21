package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AssignPracticeWorkRequest(
		@NotNull String sourceType,
		@NotNull UUID sourceId,
		UUID clientId,
		UUID assignedUserId,
		LocalDate dueDate
) {
}
