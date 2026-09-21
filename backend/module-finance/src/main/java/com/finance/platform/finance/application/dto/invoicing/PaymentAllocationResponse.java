package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationResponse(
		UUID id,
		UUID invoiceId,
		String invoiceNumber,
		BigDecimal amount,
		boolean active
) {
}
