package com.finance.platform.ai.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingSuggestion(
		String transactionType,
		UUID suggestedCategoryId,
		String suggestedCategoryCode,
		String suggestedCategoryName,
		String suggestedDescription,
		BigDecimal suggestedAmount,
		BigDecimal suggestedTaxAmount,
		LocalDate suggestedDate,
		String suggestedPaymentMethod,
		String currency,
		BigDecimal confidence,
		String reasoningSummary,
		String source
) {
}
