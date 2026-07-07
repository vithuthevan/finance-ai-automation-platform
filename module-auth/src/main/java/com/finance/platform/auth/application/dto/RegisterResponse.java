package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record RegisterResponse(
		UUID userId,
		UUID firmId,
		String firmName,
		String email,
		String fullName,
		String role
) {
}
