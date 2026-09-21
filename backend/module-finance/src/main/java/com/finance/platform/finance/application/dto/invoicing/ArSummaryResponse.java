package com.finance.platform.finance.application.dto.invoicing;

import java.math.BigDecimal;
import java.util.List;

public record ArSummaryResponse(
		BigDecimal totalOutstanding,
		BigDecimal overdue,
		BigDecimal dueThisWeek,
		BigDecimal collectedThisMonth,
		ArAgeingBuckets ageing,
		List<ArCustomerBalanceResponse> topOverdueCustomers,
		List<SalesInvoiceResponse> oldestInvoices,
		List<SalesInvoiceResponse> largestOutstanding
) {
}
