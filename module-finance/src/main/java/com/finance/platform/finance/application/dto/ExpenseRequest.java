package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
		@NotNull LocalDate transactionDate,
		@NotNull UUID categoryId,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@Size(max = 3) String currencyCode,
		@NotBlank @Size(max = 200) String vendorName,
		String description,
		BigDecimal taxAmount,
		@Size(max = 100) String referenceNo
) {
}
