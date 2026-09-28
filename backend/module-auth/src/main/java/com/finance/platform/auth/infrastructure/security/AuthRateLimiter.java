package com.finance.platform.auth.infrastructure.security;



import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitDecision;

import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitPolicy;

import com.finance.platform.auth.infrastructure.security.ratelimit.RateLimitStore;

import com.finance.platform.core.exception.ErrorCodes;

import com.finance.platform.core.exception.RateLimitedException;

import com.finance.platform.core.observability.SecurityEventLogger;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.stereotype.Component;



@Component

public class AuthRateLimiter {



	private final AuthProperties authProperties;

	private final RateLimitStore rateLimitStore;

	private final SecurityEventLogger securityEventLogger;

	private final MeterRegistry meterRegistry;



	public AuthRateLimiter(

			AuthProperties authProperties,

			RateLimitStore rateLimitStore,

			SecurityEventLogger securityEventLogger,

			ObjectProvider<MeterRegistry> meterRegistryProvider) {

		this.authProperties = authProperties;

		this.rateLimitStore = rateLimitStore;

		this.securityEventLogger = securityEventLogger;

		this.meterRegistry = meterRegistryProvider.getIfAvailable();

	}



	public void checkAllowed(String key) {

		if (!authProperties.isRateLimitEnabled()) {

			return;

		}

		RateLimitPolicy policy = resolvePolicy(key);

		RateLimitDecision decision = rateLimitStore.tryConsume(key, policy);

		if (!decision.allowed()) {

			String scope = scopeFromKey(key);

			securityEventLogger.authRateLimited(scope, key);

			if (meterRegistry != null) {

				meterRegistry.counter("auth.rate_limited", "scope", scope).increment();

			}

			throw new RateLimitedException(

					ErrorCodes.AUTH_RATE_LIMITED,

					"Too many requests. Please try again later.",

					decision.retryAfterSeconds());

		}

	}



	private static String scopeFromKey(String key) {

		if (key == null || key.isBlank()) {

			return "unknown";

		}

		int colon = key.indexOf(':');

		return colon > 0 ? key.substring(0, colon).trim().toLowerCase() : key.trim().toLowerCase();

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

			case "changepassword" -> authProperties.policyFor("changePassword", authProperties.getChangePassword());

			default -> RateLimitPolicy.defaults();

		};

	}

}


