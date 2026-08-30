package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidTransactionRequest(
		@NotBlank(message = "Void reason is required")
		@Size(max = 2000, message = "Void reason must not exceed 2000 characters")
		String reason
) {
}
