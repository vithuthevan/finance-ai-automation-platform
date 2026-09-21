package com.finance.platform.finance.application.dto.chase;

import java.util.UUID;

public record ClientChasePolicyResponse(
		UUID id,
		String name,
		boolean active,
		int[] cadenceDays
) {
}
