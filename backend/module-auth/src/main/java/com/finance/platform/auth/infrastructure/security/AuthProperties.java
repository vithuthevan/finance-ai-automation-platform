package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitPolicy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	/**
	 * When true, new self-registrations must verify email ownership before login.
	 */
	private boolean emailVerificationRequired = false;

	/** When false, auth endpoints skip rate limiting (integration tests). */
	private boolean rateLimitEnabled = true;

	/**
	 * Rate limit backing store: {@code memory} (default, single JVM) or {@code redis} for multi-instance.
	 */
	private String rateLimitStore = "memory";

	/**
	 * When true and store is memory, startup logs a warning (or fails in prod if fail-on-memory-multi-instance).
	 */
	private boolean multiInstanceDeployment = false;

	/** Fail startup in prod profile when multi-instance is flagged but store is not redis. */
	private boolean failOnInMemoryMultiInstance = true;

	@NestedConfigurationProperty
	private RateLimitPolicy login = new RateLimitPolicy(20, 60);
	@NestedConfigurationProperty
	private RateLimitPolicy register = new RateLimitPolicy(10, 3600);
	@NestedConfigurationProperty
	private RateLimitPolicy forgotPassword = new RateLimitPolicy(10, 3600);
	@NestedConfigurationProperty
	private RateLimitPolicy resetPassword = new RateLimitPolicy(20, 3600);
	@NestedConfigurationProperty
	private RateLimitPolicy refresh = new RateLimitPolicy(60, 60);
	@NestedConfigurationProperty
	private RateLimitPolicy verifyEmail = new RateLimitPolicy(30, 3600);
	@NestedConfigurationProperty
	private RateLimitPolicy resendVerification = new RateLimitPolicy(5, 3600);
	@NestedConfigurationProperty
	private RateLimitPolicy changePassword = new RateLimitPolicy(10, 3600);

	@NestedConfigurationProperty
	private LoginLockout loginLockout = new LoginLockout();

	/** Optional overrides keyed by policy name (login, register, …). */
	private Map<String, RateLimitPolicy> rateLimitPolicies = new LinkedHashMap<>();

	@Getter
	@Setter
	public static class LoginLockout {
		private int maxFailures = 5;
		private long failureWindowSeconds = 900;
		private long lockoutSeconds = 900;
	}

	public RateLimitPolicy policyFor(String name, RateLimitPolicy fallback) {
		if (rateLimitPolicies != null && rateLimitPolicies.containsKey(name)) {
			return rateLimitPolicies.get(name);
		}
		return fallback;
	}
}

