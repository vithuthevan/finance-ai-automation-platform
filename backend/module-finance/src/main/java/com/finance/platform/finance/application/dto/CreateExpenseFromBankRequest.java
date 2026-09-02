package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record CreateExpenseFromBankRequest(
		UUID categoryId,
		String vendorName,
		String description
) {
}
