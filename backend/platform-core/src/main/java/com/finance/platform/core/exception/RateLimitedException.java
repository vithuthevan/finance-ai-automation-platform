package com.finance.platform.core.exception;

public class RateLimitedException extends BusinessException {

	private final long retryAfterSeconds;

	public RateLimitedException(String message) {
		this(message, 60);
	}

	public RateLimitedException(String message, long retryAfterSeconds) {
		this(ErrorCodes.RATE_LIMITED, message, retryAfterSeconds);
	}

	public RateLimitedException(String errorCode, String message, long retryAfterSeconds) {
		super(errorCode, message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
