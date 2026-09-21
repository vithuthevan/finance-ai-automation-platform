package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ArPaymentResponse(
		UUID id,
		UUID customerId,
		LocalDate paymentDate,
		BigDecimal amount,
		BigDecimal unallocatedAmount,
		String reference,
		String status,
		String source,
		int rowVersion,
		Instant createdAt,
		List<PaymentAllocationResponse> allocations
) {
}
