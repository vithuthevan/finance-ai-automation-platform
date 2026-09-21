package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateSalesInvoiceRequest(
		@NotNull UUID customerId,
		String currency,
		String notes,
		List<SalesInvoiceLineRequest> lines
) {
}
