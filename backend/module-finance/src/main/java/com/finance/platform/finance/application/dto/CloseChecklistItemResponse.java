package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.close.CloseCheckSeverity;

public record CloseChecklistItemResponse(
		String code,
		String label,
		boolean passed,
		CloseCheckSeverity severity,
		int count
) {
}
