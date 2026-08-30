package com.finance.platform.core.exception;

import java.util.Collections;
import java.util.Map;

public class ValidationException extends BusinessException {

	private final Map<String, String> fieldErrors;

	public ValidationException(String message) {
		super(ErrorCodes.VALIDATION_FAILED, message);
		this.fieldErrors = Map.of();
	}

	public ValidationException(String field, String message) {
		super(ErrorCodes.VALIDATION_FAILED, message);
		this.fieldErrors = Map.of(field, message);
	}

	public ValidationException(String errorCode, String field, String message) {
		super(errorCode, message);
		this.fieldErrors = Map.of(field, message);
	}

	public ValidationException(Map<String, String> fieldErrors) {
		super(ErrorCodes.VALIDATION_FAILED, "One or more fields are invalid.");
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors.isEmpty() ? fieldErrors : Collections.unmodifiableMap(fieldErrors);
	}
}
