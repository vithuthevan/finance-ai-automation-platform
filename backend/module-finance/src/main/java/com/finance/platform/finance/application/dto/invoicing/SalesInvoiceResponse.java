package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesInvoiceResponse(
		UUID id,
		UUID customerId,
		String customerName,
		String invoiceNumber,
		String documentStatus,
		String settlementStatus,
		LocalDate issueDate,
		LocalDate dueDate,
		String currency,
		BigDecimal subtotal,
		BigDecimal taxTotal,
		BigDecimal total,
		BigDecimal allocatedTotal,
		BigDecimal outstanding,
		String notes,
		int rowVersion,
		Instant createdAt,
		List<SalesInvoiceLineResponse> lines
) {
}
