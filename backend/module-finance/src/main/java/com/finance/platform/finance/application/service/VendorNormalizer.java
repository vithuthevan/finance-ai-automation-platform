package com.finance.platform.finance.application.service;

public final class VendorNormalizer {

	private VendorNormalizer() {
	}

	public static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim()
				.replaceAll("(?i)\\b(pvt|private|ltd|limited|plc|inc|incorporated)\\b\\.?", "")
				.replaceAll("[^A-Za-z0-9 ]", " ")
				.replaceAll("\\s+", " ")
				.trim()
				.toUpperCase();
	}
}
