package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseRequest(
		@NotNull(message = "Transaction date is required")
		@PastOrPresent(message = "Transaction date cannot be in the future")
		LocalDate transactionDate,
		@NotNull(message = "Category is required")
		UUID categoryId,
		@NotNull(message = "Amount is required")
		@DecimalMin(value = "0.01", message = "Amount must be greater than zero")
		BigDecimal amount,
		@Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
		String currencyCode,
		@NotBlank(message = "Vendor name is required")
		@Size(max = 200, message = "Vendor name must not exceed 200 characters")
		String vendorName,
		String description,
		@DecimalMin(value = "0.00", message = "Tax amount cannot be negative")
		BigDecimal taxAmount,
		@Size(max = 100, message = "Reference number must not exceed 100 characters")
		String referenceNo
) {
}
