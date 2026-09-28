package com.finance.platform.support;

/**
 * JUnit 5 tags for selective test execution in CI and locally.
 */
public final class TestTags {

	public static final String POSTGRES_INTEGRATION = "postgres-integration";
	public static final String TENANT_SECURITY = "tenant-security";
	public static final String FLYWAY = "flyway";
	public static final String AUTH_SECURITY = "auth-security";
	public static final String SESSION_REVOCATION = "session-revocation";

	private TestTags() {
	}
}
