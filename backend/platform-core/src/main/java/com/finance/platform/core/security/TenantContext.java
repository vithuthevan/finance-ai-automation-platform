package com.finance.platform.core.security;

import java.util.Set;
import java.util.UUID;

public record TenantContext(
		UUID userId,
		UUID firmId,
		UserRole role,
		Set<UUID> accessibleClientIds
) {
	public boolean hasClientAccess(UUID clientId) {
		return role == UserRole.ADMIN || accessibleClientIds.contains(clientId);
	}
}
