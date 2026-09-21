package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationItemRequest(
		@NotNull UUID invoiceId,
		@NotNull @Positive BigDecimal amount
) {
}
