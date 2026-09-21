package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SalesInvoiceLineRequest(
		@NotBlank String description,
		@NotNull @PositiveOrZero BigDecimal quantity,
		@NotNull @PositiveOrZero BigDecimal unitPrice,
		String taxCode,
		@PositiveOrZero BigDecimal taxRatePercent
) {
}
