package com.finance.platform.finance.application.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthEndCommandCenterResponse(
		int year,
		int month,
		String periodLabel,
		YearMonth period,
		MonthEndCommandCenterSummaryResponse summary,
		List<MonthEndClientRowResponse> clients
) {
}
