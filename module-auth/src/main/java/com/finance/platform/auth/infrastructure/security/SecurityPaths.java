package com.finance.platform.auth.infrastructure.security;

public final class SecurityPaths {

	public static final String API_V1 = "/api/v1";

	public static final String AUTH_LOGIN = API_V1 + "/auth/login";
	public static final String AUTH_REGISTER = API_V1 + "/auth/register";

	public static final String EXPENSES = API_V1 + "/clients/*/expenses/**";

	public static final String HEALTH = API_V1 + "/health";

	private SecurityPaths() {
	}

}
