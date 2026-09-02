package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record CreateIncomeFromBankRequest(
		UUID categoryId,
		String payerName,
		String description
) {
}
