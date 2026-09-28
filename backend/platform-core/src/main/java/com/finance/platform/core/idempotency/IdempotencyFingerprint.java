package com.finance.platform.core.idempotency;

import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Request identity for an idempotency key. Uses raw body bytes (not canonical JSON)
 * plus method, path, and a sorted query string. Volatile headers are excluded.
 */
public final class IdempotencyFingerprint {

	public static final int MAX_KEY_LENGTH = 255;
	public static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
	public static final int MAX_STORED_RESPONSE_CHARS = 256 * 1024;

	private static final Pattern KEY_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,255}$");

	private IdempotencyFingerprint() {
	}

	public static void validateKey(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new BusinessException(ErrorCodes.IDEMPOTENCY_KEY_REQUIRED,
					"Idempotency-Key header is required for this operation");
		}
		String key = rawKey.trim();
		if (!KEY_PATTERN.matcher(key).matches()) {
			throw new BusinessException(ErrorCodes.VALIDATION_FAILED,
					"Idempotency-Key must be 1-255 printable ASCII characters without whitespace");
		}
	}

	public static String normalizeKey(String rawKey) {
		validateKey(rawKey);
		return rawKey.trim();
	}

	/**
	 * When {@code canonicalQuery} is empty this matches the historical
	 * {@code method + " " + path + " " + bodyHash} fingerprint so completed keys keep replaying.
	 */
	public static String requestIdentity(String method, String path, String canonicalQuery, String bodyHash) {
		if (canonicalQuery == null || canonicalQuery.isEmpty()) {
			return method + " " + path + " " + bodyHash;
		}
		return method + " " + path + "?" + canonicalQuery + " " + bodyHash;
	}

	public static String canonicalQuery(String rawQuery) {
		if (rawQuery == null || rawQuery.isEmpty()) {
			return "";
		}
		String[] parts = rawQuery.split("&", -1);
		Arrays.sort(parts);
		return String.join("&", parts);
	}
}
