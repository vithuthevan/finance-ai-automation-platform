package com.finance.platform.finance.application.bankimport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record CsvColumnMapping(
		int dateColumn,
		int descriptionColumn,
		int referenceColumn,
		int debitColumn,
		int creditColumn,
		int balanceColumn,
		Integer amountColumn,
		String dateFormat,
		boolean headerRow
) {
	public static CsvColumnMapping defaults() {
		return new CsvColumnMapping(0, 1, 2, 3, 4, 5, null, "AUTO", true);
	}

	public DateTimeFormatter[] dateFormatters() {
		if (dateFormat == null || dateFormat.isBlank() || "AUTO".equalsIgnoreCase(dateFormat)) {
			return new DateTimeFormatter[] {
					DateTimeFormatter.ISO_LOCAL_DATE,
					DateTimeFormatter.ofPattern("dd/MM/yyyy"),
					DateTimeFormatter.ofPattern("MM/dd/yyyy"),
					DateTimeFormatter.ofPattern("dd-MM-yyyy"),
					DateTimeFormatter.ofPattern("yyyy/MM/dd")
			};
		}
		return new DateTimeFormatter[] { DateTimeFormatter.ofPattern(dateFormat, Locale.ENGLISH) };
	}
}
