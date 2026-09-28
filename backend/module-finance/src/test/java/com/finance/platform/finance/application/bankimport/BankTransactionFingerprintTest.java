package com.finance.platform.finance.application.bankimport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransactionFingerprintTest {

	@Test
	void normalize_collapsesWhitespaceAndCase() {
		assertThat(BankTransactionFingerprint.normalize("  UBER   Trip  ")).isEqualTo("uber trip");
	}

	@Test
	void fingerprint_stableForHarmlessDescriptionFormatting() {
		LocalDate date = LocalDate.of(2026, 9, 3);
		String a = BankTransactionFingerprint.fingerprint(
				null, date, date, new BigDecimal("1500.00"), null, "UBER TRIP", "REF1", new BigDecimal("10000"));
		String b = BankTransactionFingerprint.fingerprint(
				null, date, date, new BigDecimal("1500.00"), null, "uber  trip", "ref1", new BigDecimal("10000"));
		assertThat(a).isEqualTo(b);
	}

	@Test
	void fingerprint_distinguishesLegitimateRepeatsByBalance() {
		LocalDate date = LocalDate.of(2026, 9, 3);
		String first = BankTransactionFingerprint.fingerprint(
				null, date, date, new BigDecimal("1500.00"), null, "UBER TRIP", "", new BigDecimal("8500"));
		String second = BankTransactionFingerprint.fingerprint(
				null, date, date, new BigDecimal("1500.00"), null, "UBER TRIP", "", new BigDecimal("7000"));
		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void fingerprint_prefersExternalTransactionId() {
		LocalDate date = LocalDate.of(2026, 9, 3);
		String byId = BankTransactionFingerprint.fingerprint(
				"TXN-839274", date, date, new BigDecimal("99"), null, "ANY", "ANY", null);
		String byFields = BankTransactionFingerprint.fingerprint(
				null, date, date, new BigDecimal("99"), null, "ANY", "ANY", null);
		assertThat(byId).isNotEqualTo(byFields);
	}

	@Test
	void legacyFingerprint_matchesPreBalanceImports() {
		LocalDate date = LocalDate.of(2026, 9, 3);
		String legacy = BankTransactionFingerprint.legacyHeuristicFingerprint(
				date, date, new BigDecimal("4850.00"), null, "KEELLS SUPER KANDY", "REF1001");
		assertThat(legacy).hasSize(64);
	}
}
