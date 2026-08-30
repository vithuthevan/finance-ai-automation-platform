package com.finance.platform.finance.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeResponse(
		UUID id,
		UUID clientId,
		UUID categoryId,
		LocalDate transactionDate,
		BigDecimal amount,
		String currencyCode,
		String customerName,
		String description,
		String paymentMethod,
		BigDecimal taxAmount,
		String referenceNo,
		String status,
		String source,
		UUID createdByUserId,
		Instant approvedAt,
		Instant voidedAt,
		String voidReason,
		UUID primaryDocumentId,
		java.util.List<UUID> documentIds,
		Instant createdAt,
		Instant updatedAt
) {
}
