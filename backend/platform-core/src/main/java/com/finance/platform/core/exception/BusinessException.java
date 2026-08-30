package com.finance.platform.core.exception;

public class BusinessException extends RuntimeException {

	private final String errorCode;

	public BusinessException(String message) {
		this(ErrorCodes.BUSINESS_RULE_VIOLATION, message);
	}

	public BusinessException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode == null ? ErrorCodes.BUSINESS_RULE_VIOLATION : errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}
}
