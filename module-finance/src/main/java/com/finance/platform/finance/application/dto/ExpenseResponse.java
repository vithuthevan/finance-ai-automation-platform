package com.finance.platform.finance.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
		UUID id,
		UUID clientId,
		UUID categoryId,
		LocalDate transactionDate,
		BigDecimal amount,
		String currencyCode,
		String vendorName,
		String description,
		BigDecimal taxAmount,
		String referenceNo,
		String status,
		String source,
		Instant approvedAt,
		Instant createdAt,
		Instant updatedAt
) {
}
