package com.finance.platform.finance.application.dto;

import java.util.List;
import java.util.UUID;

public record FirmOnboardingChecklistResponse(
		UUID firmId,
		String firmName,
		List<OnboardingStepResponse> steps,
		int completedCount,
		int totalCount
) {
}
