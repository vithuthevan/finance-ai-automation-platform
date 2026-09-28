package com.finance.platform.auth.infrastructure.security;

/**
 * Canonical normalization for login identifiers used in rate limits and lockout counters.
 */
public final class AuthIdentifierNormalizer {

	private AuthIdentifierNormalizer() {
	}

	public static String normalizeEmail(String email) {
		if (email == null) {
			return "unknown";
		}
		String trimmed = email.trim();
		return trimmed.isEmpty() ? "unknown" : trimmed.toLowerCase();
	}
}
