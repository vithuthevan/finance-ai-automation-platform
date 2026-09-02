package com.finance.platform.finance.application.bankimport;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic CSV bank statement importer with configurable column mapping.
 * Parses line-by-line; does not load entire files into multiple in-memory structures beyond the parsed rows.
 */
@Component
public class GenericBankStatementCsvImporter implements BankStatementImporter {

	private static final int SAMPLE_LIMIT = 8;

	@Override
	public ImportPreview preview(byte[] content, CsvColumnMapping mapping) {
		ImportParseResult parsed = parse(content, mapping);
		Set<String> hashes = new HashSet<>();
		int duplicates = 0;
		for (ParsedBankRow row : parsed.validRows()) {
			if (!hashes.add(row.rowHash())) {
				duplicates++;
			}
		}
		LocalDate from = null;
		LocalDate to = null;
		for (ParsedBankRow row : parsed.validRows()) {
			if (from == null || row.txnDate().isBefore(from)) {
				from = row.txnDate();
			}
			if (to == null || row.txnDate().isAfter(to)) {
				to = row.txnDate();
			}
		}
		List<ImportPreviewRow> sample = parsed.validRows().stream()
				.limit(SAMPLE_LIMIT)
				.map(row -> new ImportPreviewRow(
						row.sourceLineNumber(),
						row.txnDate(),
						row.description(),
						row.referenceNo(),
						row.debit(),
						row.credit(),
						row.balance()))
				.toList();
		int dataRows = parsed.validRows().size() + parsed.errors().size();
		return new ImportPreview(
				dataRows,
				parsed.validRows().size(),
				parsed.errors().size(),
				duplicates,
				from,
				to,
				sample,
				parsed.errors());
	}

	@Override
	public ImportParseResult parse(byte[] content, CsvColumnMapping mapping) {
		CsvColumnMapping cols = mapping == null ? CsvColumnMapping.defaults() : mapping;
		String text = new String(content, StandardCharsets.UTF_8);
		String[] lines = text.split("\\r?\\n");
		List<ParsedBankRow> valid = new ArrayList<>();
		List<ImportRowError> errors = new ArrayList<>();
		boolean headerSkipped = !cols.headerRow();
		for (int i = 0; i < lines.length; i++) {
			int lineNumber = i + 1;
			String line = lines[i];
			if (line.isBlank()) {
				continue;
			}
			if (!headerSkipped && cols.headerRow()) {
				headerSkipped = true;
				continue;
			}
			String[] parts = splitCsvLine(line);
			try {
				ParsedBankRow row = parseRow(parts, cols, lineNumber);
				valid.add(row);
			} catch (IllegalArgumentException ex) {
				errors.add(new ImportRowError(lineNumber, ex.getMessage()));
			}
		}
		return new ImportParseResult(valid, errors);
	}

	private ParsedBankRow parseRow(String[] parts, CsvColumnMapping cols, int lineNumber) {
		LocalDate txnDate = parseDate(parts, cols.dateColumn(), cols.dateFormatters());
		if (txnDate == null) {
			throw new IllegalArgumentException("Invalid or missing date");
		}
		String description = safe(parts, cols.descriptionColumn());
		String reference = safe(parts, cols.referenceColumn());
		BigDecimal debit = parseAmount(parts, cols.debitColumn());
		BigDecimal credit = parseAmount(parts, cols.creditColumn());
		if (cols.amountColumn() != null && cols.amountColumn() >= 0) {
			BigDecimal signed = parseAmount(parts, cols.amountColumn());
			if (signed != null) {
				if (signed.signum() < 0) {
					debit = signed.abs();
					credit = null;
				} else if (signed.signum() > 0) {
					credit = signed;
					debit = null;
				}
			}
		}
		if (debit != null && credit != null && debit.signum() > 0 && credit.signum() > 0) {
			throw new IllegalArgumentException("Debit and credit both populated");
		}
		if ((debit == null || debit.signum() == 0) && (credit == null || credit.signum() == 0)) {
			throw new IllegalArgumentException("No debit or credit amount");
		}
		BigDecimal balance = parseAmount(parts, cols.balanceColumn());
		String hash = rowHash(txnDate, debit, credit, description, reference);
		return new ParsedBankRow(lineNumber, txnDate, txnDate, description, reference, debit, credit, balance, hash);
	}

	static String rowHash(LocalDate date, BigDecimal debit, BigDecimal credit, String description, String reference) {
		String payload = String.join("|",
				date.toString(),
				debit == null ? "" : debit.stripTrailingZeros().toPlainString(),
				credit == null ? "" : credit.stripTrailingZeros().toPlainString(),
				normalize(description),
				normalize(reference));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			return Integer.toHexString(payload.hashCode());
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static LocalDate parseDate(String[] parts, int index, DateTimeFormatter[] formatters) {
		String value = safe(parts, index);
		if (value == null || value.isBlank()) {
			return null;
		}
		String cleaned = value.trim().replace("\"", "");
		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.parse(cleaned, formatter);
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static BigDecimal parseAmount(String[] parts, int index) {
		String value = safe(parts, index);
		if (value == null || value.isBlank()) {
			return null;
		}
		String cleaned = value.replace(",", "").replace("\"", "").trim();
		if (cleaned.isEmpty() || "-".equals(cleaned)) {
			return null;
		}
		try {
			return new BigDecimal(cleaned);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String safe(String[] parts, int index) {
		if (index < 0 || index >= parts.length) {
			return null;
		}
		String value = parts[index].trim();
		return value.isEmpty() ? null : value.replace("\"", "");
	}

	static String[] splitCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				inQuotes = !inQuotes;
			} else if (ch == ',' && !inQuotes) {
				fields.add(current.toString());
				current.setLength(0);
			} else {
				current.append(ch);
			}
		}
		fields.add(current.toString());
		return fields.toArray(String[]::new);
	}
}
