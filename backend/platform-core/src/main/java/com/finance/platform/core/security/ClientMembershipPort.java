package com.finance.platform.core.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Membership and assignment lookups without exposing auth persistence to finance.
 */
public interface ClientMembershipPort {

	boolean hasAccessToClient(UUID userId, UUID clientId);

	Optional<ClientAccessType> assignedAccessType(UUID userId, UUID clientId);

	Set<UUID> accessibleClientIds(UUID userId);
}
