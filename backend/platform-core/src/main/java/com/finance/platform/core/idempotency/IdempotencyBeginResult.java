package com.finance.platform.core.idempotency;

import java.util.UUID;

public record IdempotencyBeginResult(
		Kind kind,
		UUID claimId,
		Integer statusCode,
		String responseBody
) {
	public enum Kind {
		PROCEED,
		REPLAY,
		IN_PROGRESS
	}

	public static IdempotencyBeginResult proceed(UUID claimId) {
		return new IdempotencyBeginResult(Kind.PROCEED, claimId, null, null);
	}

	public static IdempotencyBeginResult replay(int statusCode, String responseBody) {
		return new IdempotencyBeginResult(Kind.REPLAY, null, statusCode, responseBody);
	}

	public static IdempotencyBeginResult inProgress() {
		return new IdempotencyBeginResult(Kind.IN_PROGRESS, null, null, null);
	}
}
