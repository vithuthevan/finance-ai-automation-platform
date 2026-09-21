package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesInvoiceLineResponse(
		UUID id,
		int lineNo,
		String description,
		BigDecimal quantity,
		BigDecimal unitPrice,
		BigDecimal lineTotal,
		String taxCode,
		BigDecimal taxRatePercent
) {
}
