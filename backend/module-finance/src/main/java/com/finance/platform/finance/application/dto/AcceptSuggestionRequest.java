package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AcceptSuggestionRequest(
		String transactionType,
		UUID categoryId,
		LocalDate transactionDate,
		BigDecimal amount,
		String currencyCode,
		String partyName,
		String description,
		BigDecimal taxAmount,
		String referenceNo,
		PaymentMethod paymentMethod
) {
}
