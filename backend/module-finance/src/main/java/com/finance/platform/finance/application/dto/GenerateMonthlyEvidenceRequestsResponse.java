package com.finance.platform.finance.application.dto;

import java.util.List;
import java.util.UUID;

public record GenerateMonthlyEvidenceRequestsResponse(
		int created,
		List<UUID> requestIds,
		List<String> skippedTitles
) {
}
