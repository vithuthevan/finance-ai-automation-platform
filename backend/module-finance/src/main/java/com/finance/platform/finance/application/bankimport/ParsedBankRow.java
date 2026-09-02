package com.finance.platform.finance.application.bankimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedBankRow(
		int sourceLineNumber,
		LocalDate txnDate,
		LocalDate valueDate,
		String description,
		String referenceNo,
		BigDecimal debit,
		BigDecimal credit,
		BigDecimal balance,
		String rowHash
) {
}
