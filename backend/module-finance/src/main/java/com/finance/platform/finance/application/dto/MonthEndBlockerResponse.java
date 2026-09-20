package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.close.CloseCheckSeverity;

import java.util.List;

public record MonthEndBlockerResponse(
		CloseCheckSeverity severity,
		String code,
		String message,
		int count,
		String actionHint,
		CloseActionLinkResponse action
) {
}
