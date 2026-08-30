package com.finance.platform.auth.application.dto;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
		UUID id,
		UUID firmId,
		String email,
		String fullName,
		String role,
		Set<UUID> accessibleClientIds,
		boolean uploadOnly
) {
}
