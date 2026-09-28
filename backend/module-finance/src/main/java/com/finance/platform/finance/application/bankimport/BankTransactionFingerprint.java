package com.finance.platform.finance.application.bankimport;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic transaction identity for bank CSV rows (scoped at persistence by bank account).
 * <p>
 * Level 1: bank-provided transaction id when mapped and present.
 * Level 2: heuristic fingerprint from normalized financial fields (includes balance when present).
 */
public final class BankTransactionFingerprint {

	private static final Pattern COLLAPSE_WHITESPACE = Pattern.compile("\\s+");

	private BankTransactionFingerprint() {
	}

	public static String fingerprint(
			String externalTransactionId,
			LocalDate txnDate,
			LocalDate valueDate,
			BigDecimal debit,
			BigDecimal credit,
			String description,
			String referenceNo,
			BigDecimal balance
	) {
		if (externalTransactionId != null && !externalTransactionId.isBlank()) {
			return digest("ext|" + normalize(externalTransactionId));
		}
		return heuristicFingerprint(txnDate, valueDate, debit, credit, description, referenceNo, balance, true);
	}

	/**
	 * Pre-balance fingerprint used by imports before balance was included; kept for duplicate lookup fallback.
	 */
	public static String legacyHeuristicFingerprint(
			LocalDate txnDate,
			LocalDate valueDate,
			BigDecimal debit,
			BigDecimal credit,
			String description,
			String referenceNo
	) {
		return heuristicFingerprint(txnDate, valueDate, debit, credit, description, referenceNo, null, false);
	}

	private static String heuristicFingerprint(
			LocalDate txnDate,
			LocalDate valueDate,
			BigDecimal debit,
			BigDecimal credit,
			String description,
			String referenceNo,
			BigDecimal balance,
			boolean includeBalance
	) {
		String version = includeBalance ? "v2" : "v1";
		String payload = String.join("|",
				version,
				txnDate == null ? "" : txnDate.toString(),
				valueDate == null ? "" : valueDate.toString(),
				amountPart(debit),
				amountPart(credit),
				normalize(description),
				normalize(referenceNo),
				includeBalance && balance != null ? amountPart(balance) : "");
		return digest(payload);
	}

	static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return COLLAPSE_WHITESPACE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
	}

	private static String amountPart(BigDecimal amount) {
		return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
	}

	private static String digest(String payload) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			return Integer.toHexString(payload.hashCode());
		}
	}
}
