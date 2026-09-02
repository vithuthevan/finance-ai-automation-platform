package com.finance.platform.finance.application.bankimport;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportPreviewRow(
		int lineNumber,
		LocalDate txnDate,
		String description,
		String referenceNo,
		BigDecimal debit,
		BigDecimal credit,
		BigDecimal balance
) {
}
