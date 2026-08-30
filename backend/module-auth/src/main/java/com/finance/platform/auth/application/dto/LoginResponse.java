package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record LoginResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		UUID userId,
		String email,
		String fullName,
		String role,
		String refreshToken,
		boolean uploadOnly
) {

	public LoginResponse(
			String accessToken,
			String tokenType,
			long expiresIn,
			UUID userId,
			String email,
			String fullName,
			String role
	) {
		this(accessToken, tokenType, expiresIn, userId, email, fullName, role, null, false);
	}

	public LoginResponse(
			String accessToken,
			String tokenType,
			long expiresIn,
			UUID userId,
			String email,
			String fullName,
			String role,
			String refreshToken
	) {
		this(accessToken, tokenType, expiresIn, userId, email, fullName, role, refreshToken, false);
	}
}
