package com.finance.platform.auth.infrastructure.security;

/**
 * Spring Security role names for URL- or method-level authorization.
 * Values match {@code SecurityUser} authorities ({@code ROLE_*} prefix is added by Spring).
 */
public final class SecurityRoles {

	public static final String ADMIN = "ADMIN";
	public static final String ACCOUNTANT = "ACCOUNTANT";
	public static final String AUDITOR = "AUDITOR";
	public static final String BUSINESS_OWNER = "BUSINESS_OWNER";

	private SecurityRoles() {
	}

}
