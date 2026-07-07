package com.finance.platform.core.exception;

import java.util.Collections;
import java.util.Map;

public class ValidationException extends BusinessException {

	private final Map<String, String> fieldErrors;

	public ValidationException(String message) {
		super(message);
		this.fieldErrors = Map.of();
	}

	public ValidationException(String field, String message) {
		super(message);
		this.fieldErrors = Map.of(field, message);
	}

	public ValidationException(Map<String, String> fieldErrors) {
		super("One or more fields are invalid.");
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors.isEmpty() ? fieldErrors : Collections.unmodifiableMap(fieldErrors);
	}
}
