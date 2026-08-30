package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeRequest(
		@NotNull @PastOrPresent LocalDate transactionDate,
		@NotNull UUID categoryId,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@Size(min = 3, max = 3) String currencyCode,
		@NotBlank @Size(max = 200) String customerName,
		String description,
		PaymentMethod paymentMethod,
		@DecimalMin("0.00") BigDecimal taxAmount,
		@Size(max = 100) String referenceNo
) {
}
