package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record LoginResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		UUID userId,
		String email,
		String fullName,
		String role
) {
}
