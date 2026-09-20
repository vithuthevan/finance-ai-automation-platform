package com.finance.platform.finance.application.dto;

/**
 * Concise workflow step indicator for portfolio rows (documents, bank, etc.).
 */
public record MonthEndProgressStepResponse(
		String code,
		String label,
		String indicator
) {
}
