package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ArPaymentRequest(
		UUID customerId,
		@NotNull LocalDate paymentDate,
		@NotNull @Positive BigDecimal amount,
		String reference,
		String source
) {
}
