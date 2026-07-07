package com.finance.platform.core.exception;

public class DuplicateResourceException extends BusinessException {

	public DuplicateResourceException(String resource, String field, Object value) {
		super("%s already exists with %s: %s".formatted(resource, field, value));
	}
}
