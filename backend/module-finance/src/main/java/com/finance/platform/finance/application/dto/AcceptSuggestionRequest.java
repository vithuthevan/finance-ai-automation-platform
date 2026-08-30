package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record AcceptSuggestionRequest(
		String transactionType,
		UUID categoryId
) {
}
