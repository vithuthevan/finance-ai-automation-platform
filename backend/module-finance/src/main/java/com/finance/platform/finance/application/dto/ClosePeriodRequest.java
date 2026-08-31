package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.Size;

public record ClosePeriodRequest(
		@Size(max = 2000) String closeNote
) {
}
