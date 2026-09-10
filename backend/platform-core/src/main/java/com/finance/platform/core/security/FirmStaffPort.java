package com.finance.platform.core.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only staff directory used by finance workflows. Implemented by module-auth.
 */
public interface FirmStaffPort {

	Optional<String> displayName(UUID userId);

	Set<UUID> adminIds(UUID firmId);

	boolean isActiveInFirmWithRoles(UUID userId, UUID firmId, Set<UserRole> roles);

	Set<UUID> clientUserIdsByRole(UUID clientId, UserRole role, boolean excludeReadOnly);

	List<StaffMember> activeStaff(UUID firmId);

	record StaffMember(UUID id, String fullName, UserRole role) {
	}
}
