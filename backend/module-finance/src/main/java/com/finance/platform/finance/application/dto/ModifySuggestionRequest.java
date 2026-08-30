package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ModifySuggestionRequest(
		@NotBlank String transactionType,
		@NotNull LocalDate transactionDate,
		@NotNull UUID categoryId,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		String currencyCode,
		@NotBlank String partyName,
		String description,
		BigDecimal taxAmount,
		String referenceNo,
		PaymentMethod paymentMethod
) {
}
