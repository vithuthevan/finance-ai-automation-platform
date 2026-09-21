package com.finance.platform.finance.application.dto.chase;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record UpsertClientChasePolicyRequest(
		@NotNull Boolean enabled,
		@NotEmpty int[] cadenceDays
) {
}
