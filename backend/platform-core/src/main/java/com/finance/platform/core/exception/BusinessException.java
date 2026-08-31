package com.finance.platform.core.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

	private final String errorCode;
	private final Map<String, Object> properties;

	public BusinessException(String message) {
		this(ErrorCodes.BUSINESS_RULE_VIOLATION, message);
	}

	public BusinessException(String errorCode, String message) {
		this(errorCode, message, Map.of());
	}

	public BusinessException(String errorCode, String message, Map<String, Object> properties) {
		super(message);
		this.errorCode = errorCode == null ? ErrorCodes.BUSINESS_RULE_VIOLATION : errorCode;
		this.properties = properties == null || properties.isEmpty() ? Map.of() : Map.copyOf(properties);
	}

	public String getErrorCode() {
		return errorCode;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}
}
