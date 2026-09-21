package com.finance.platform.finance.application.dto.invoicing;

import java.util.List;

public record UpdateSalesInvoiceDraftRequest(
		String notes,
		List<SalesInvoiceLineRequest> lines
) {
}
