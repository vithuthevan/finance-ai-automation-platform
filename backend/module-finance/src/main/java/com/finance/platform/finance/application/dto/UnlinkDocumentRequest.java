package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record UnlinkDocumentRequest(
		UUID expenseId,
		UUID incomeId
) {
}
