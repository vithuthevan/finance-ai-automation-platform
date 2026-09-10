package com.finance.platform.core.exception;

public class RateLimitedException extends BusinessException {

	public RateLimitedException(String message) {
		super(ErrorCodes.RATE_LIMITED, message);
	}
}
