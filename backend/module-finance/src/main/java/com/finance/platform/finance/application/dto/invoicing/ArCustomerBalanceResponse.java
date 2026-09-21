package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.util.UUID;

public record ArCustomerBalanceResponse(
		UUID customerId,
		String customerName,
		BigDecimal outstanding,
		BigDecimal overdue
) {
}
