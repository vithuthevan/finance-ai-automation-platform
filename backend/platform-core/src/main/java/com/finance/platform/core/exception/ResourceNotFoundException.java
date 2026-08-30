package com.finance.platform.core.exception;

public class ResourceNotFoundException extends BusinessException {

	public ResourceNotFoundException(String resource, Object id) {
		super(ErrorCodes.notFound(resource), "%s not found: %s".formatted(resource, id));
	}
}
