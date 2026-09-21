package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GenerateMonthlyEvidenceRequestsRequest(
		@NotNull @Min(2000) @Max(2100) Integer year,
		@NotNull @Min(1) @Max(12) Integer month,
		LocalDate dueDate,
		List<UUID> itemIds
) {
}
