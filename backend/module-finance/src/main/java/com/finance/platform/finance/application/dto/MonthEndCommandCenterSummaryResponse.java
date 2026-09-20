package com.finance.platform.finance.application.dto;

import java.util.List;

public record MonthEndCommandCenterSummaryResponse(
		int totalClients,
		int ready,
		int needsAttention,
		int blocked,
		int closed,
		long overdueDocumentRequests,
		long openDocumentRequests
) {
}
