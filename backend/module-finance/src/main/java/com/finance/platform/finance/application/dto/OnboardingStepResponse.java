package com.finance.platform.finance.application.dto;

import java.util.List;
import java.util.UUID;

public record OnboardingStepResponse(
		String code,
		String label,
		boolean completed,
		String actionPath
) {
}
