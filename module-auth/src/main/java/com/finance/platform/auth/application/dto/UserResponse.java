package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record UserResponse(
		UUID id,
		UUID firmId,
		String email,
		String fullName,
		String role,
		boolean active
) {
}
