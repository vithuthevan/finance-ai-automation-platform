package com.finance.platform.auth.application.dto;

import java.util.List;
import java.util.UUID;

public record UserResponse(
		UUID id,
		UUID firmId,
		String email,
		String fullName,
		String role,
		boolean active,
		List<ClientAccessResponse> clientAccess
) {

	public UserResponse(UUID id, UUID firmId, String email, String fullName, String role, boolean active) {
		this(id, firmId, email, fullName, role, active, List.of());
	}
}
