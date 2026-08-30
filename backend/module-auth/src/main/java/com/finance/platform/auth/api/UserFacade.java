package com.finance.platform.auth.api;

import com.finance.platform.auth.domain.model.UserClientAccess;
import com.finance.platform.core.security.UserRole;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserFacade {

	UserSummary getUser(UUID userId);

	Set<UUID> getAccessibleClientIds(UUID userId);

	boolean hasAccessToClient(UUID userId, UUID clientId);

	Optional<UserClientAccess.AccessType> getAssignedAccessType(UUID userId, UUID clientId);

	boolean isUploadOnlyWorkspace(UUID userId);

	record UserSummary(UUID id, UUID firmId, String email, String fullName, UserRole role) {
	}
}
