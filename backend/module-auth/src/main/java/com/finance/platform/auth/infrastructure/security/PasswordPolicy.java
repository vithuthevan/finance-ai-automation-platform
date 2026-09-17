package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.core.exception.ValidationException;

public final class PasswordPolicy {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 100;

	private PasswordPolicy() {
	}

	public static void validate(String password, String fieldName) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new ValidationException(fieldName, "Password must be at least 8 characters");
		}
		if (password.length() > MAX_LENGTH) {
			throw new ValidationException(fieldName, "Password must not exceed 100 characters");
		}
		boolean hasLetter = false;
		boolean hasDigit = false;
		for (int i = 0; i < password.length(); i++) {
			char character = password.charAt(i);
			if (Character.isLetter(character)) {
				hasLetter = true;
			}
			if (Character.isDigit(character)) {
				hasDigit = true;
			}
		}
		if (!hasLetter || !hasDigit) {
			throw new ValidationException(fieldName, "Password must contain at least one letter and one digit");
		}
	}
}
