package com.finance.platform.core.exception;

public class DuplicateResourceException extends BusinessException {

	public DuplicateResourceException(String resource, String field, Object value) {
		super(ErrorCodes.DUPLICATE_RESOURCE, "%s already exists with %s: %s".formatted(resource, field, value));
	}

	public DuplicateResourceException(String errorCode, String resource, String field, Object value) {
		super(errorCode, "%s already exists with %s: %s".formatted(resource, field, value));
	}
}
