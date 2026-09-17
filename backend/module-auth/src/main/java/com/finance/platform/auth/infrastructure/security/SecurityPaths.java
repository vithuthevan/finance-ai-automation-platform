package com.finance.platform.auth.infrastructure.security;

public final class SecurityPaths {

	public static final String API_V1 = "/api/v1";

	public static final String AUTH_LOGIN = API_V1 + "/auth/login";
	public static final String AUTH_REGISTER = API_V1 + "/auth/register";
	public static final String AUTH_REFRESH = API_V1 + "/auth/refresh";
	public static final String AUTH_FORGOT = API_V1 + "/auth/forgot-password";
	public static final String AUTH_RESET = API_V1 + "/auth/reset-password";
	public static final String AUTH_LOGOUT = API_V1 + "/auth/logout";
	public static final String AUTH_VERIFY_EMAIL = API_V1 + "/auth/verify-email";

	public static final String EXPENSES = API_V1 + "/clients/*/expenses/**";

	public static final String HEALTH = API_V1 + "/health";

	public static final String ACTUATOR_HEALTH = "/actuator/health";

	public static final String[] OPENAPI = {
			"/v3/api-docs",
			"/v3/api-docs/**",
			"/swagger-ui.html",
			"/swagger-ui/**"
	};

	private SecurityPaths() {
	}

}
