package com.finance.platform.auth.infrastructure.security.ratelimit;

public record RateLimitPolicy(int maxAttempts, long windowSeconds) {

	public RateLimitPolicy {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be >= 1");
		}
		if (windowSeconds < 1) {
			throw new IllegalArgumentException("windowSeconds must be >= 1");
		}
	}

	public static RateLimitPolicy defaults() {
		return new RateLimitPolicy(20, 60);
	}
}
