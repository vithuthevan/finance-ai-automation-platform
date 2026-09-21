package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.close.CloseCheckSeverity;
import com.finance.platform.finance.application.close.CloseResponsibility;

public record CloseFindingResponse(
		CloseCheckSeverity severity,
		String code,
		String message,
		int count,
		String actionHint,
		CloseResponsibility responsibility,
		String responsibilityLabel
) {
}
