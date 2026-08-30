package com.finance.platform.ai.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExtractedDocument(
		String documentType,
		String merchantOrCustomer,
		String invoiceNumber,
		LocalDate date,
		LocalDate dueDate,
		String currency,
		BigDecimal subtotal,
		BigDecimal tax,
		BigDecimal totalAmount,
		String paymentMethod,
		String description,
		String suggestedTransactionType,
		String candidateCategoryCode,
		BigDecimal confidence,
		String rawJson
) {
}
