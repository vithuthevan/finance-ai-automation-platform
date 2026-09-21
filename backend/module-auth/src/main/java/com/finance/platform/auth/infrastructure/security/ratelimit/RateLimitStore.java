package com.finance.platform.auth.infrastructure.security.ratelimit;

public interface RateLimitStore {

	RateLimitDecision tryConsume(String key, RateLimitPolicy policy);
}
