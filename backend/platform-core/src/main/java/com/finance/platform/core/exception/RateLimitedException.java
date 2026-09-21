package com.finance.platform.core.exception;

public class RateLimitedException extends BusinessException {

	private final long retryAfterSeconds;

	public RateLimitedException(String message) {
		this(message, 60);
	}

	public RateLimitedException(String message, long retryAfterSeconds) {
		super(ErrorCodes.RATE_LIMITED, message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
