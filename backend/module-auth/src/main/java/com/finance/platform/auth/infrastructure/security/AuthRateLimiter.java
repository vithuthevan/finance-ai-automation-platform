package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitDecision;
import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitPolicy;
import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitStore;
import com.finance.platform.core.exception.RateLimitedException;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

	private final AuthProperties authProperties;
	private final RateLimitStore rateLimitStore;

	public AuthRateLimiter(AuthProperties authProperties, RateLimitStore rateLimitStore) {
		this.authProperties = authProperties;
		this.rateLimitStore = rateLimitStore;
	}

	public void checkAllowed(String key) {
		if (!authProperties.isRateLimitEnabled()) {
			return;
		}
		RateLimitPolicy policy = resolvePolicy(key);
		RateLimitDecision decision = rateLimitStore.tryConsume(key, policy);
		if (!decision.allowed()) {
			throw new RateLimitedException("Too many requests. Please try again later.", decision.retryAfterSeconds());
		}
	}

	private RateLimitPolicy resolvePolicy(String key) {
		if (key == null) {
			return RateLimitPolicy.defaults();
		}
		String prefix = key.split(":", 2)[0].trim().toLowerCase();
		return switch (prefix) {
			case "login" -> authProperties.policyFor("login", authProperties.getLogin());
			case "register" -> authProperties.policyFor("register", authProperties.getRegister());
			case "forgot" -> authProperties.policyFor("forgotPassword", authProperties.getForgotPassword());
			case "reset" -> authProperties.policyFor("resetPassword", authProperties.getResetPassword());
			case "refresh" -> authProperties.policyFor("refresh", authProperties.getRefresh());
			case "verify" -> authProperties.policyFor("verifyEmail", authProperties.getVerifyEmail());
			case "resend" -> authProperties.policyFor("resendVerification", authProperties.getResendVerification());
			default -> RateLimitPolicy.defaults();
		};
	}
}
