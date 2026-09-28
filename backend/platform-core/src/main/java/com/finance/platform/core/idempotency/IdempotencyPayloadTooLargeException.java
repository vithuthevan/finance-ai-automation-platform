package com.finance.platform.core.idempotency;

final class IdempotencyPayloadTooLargeException extends RuntimeException {

	IdempotencyPayloadTooLargeException() {
		super("Request body exceeds the maximum size that can be fingerprinted");
	}
}
