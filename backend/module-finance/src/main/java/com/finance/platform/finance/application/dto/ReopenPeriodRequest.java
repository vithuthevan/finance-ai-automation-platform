package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenPeriodRequest(
		@NotBlank(message = "Reopen reason is required")
		@Size(max = 2000, message = "Reopen reason must not exceed 2000 characters")
		String reason
) {
}
