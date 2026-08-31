package com.finance.platform.ai.provider;

import com.finance.platform.ai.application.DocumentExtractionService;
import com.finance.platform.ai.application.ExtractedDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockExtractionProvider implements DocumentExtractionService {

	private static final Pattern AMOUNT = Pattern.compile("(\\d+[._]\\d{2})");

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public Optional<ExtractedDocument> extract(byte[] content, String fileName, String mimeType) {
		String name = fileName == null ? "" : fileName;
		BigDecimal amount = null;
		Matcher matcher = AMOUNT.matcher(name.replace(',', '.'));
		if (matcher.find()) {
			try {
				amount = new BigDecimal(matcher.group(1).replace('_', '.'));
			} catch (Exception ignored) {
				amount = null;
			}
		}
		String type = name.toLowerCase().contains("invoice") || name.toLowerCase().contains("sales")
				? "INCOME"
				: "EXPENSE";
		return Optional.of(new ExtractedDocument(
				type.equals("INCOME") ? "SALES_INVOICE" : "RECEIPT",
				guessParty(name),
				null,
				null,
				null,
				LocalDate.now(),
				null,
				"LKR",
				amount,
				null,
				null,
				amount,
				null,
				"Suggested from file name only",
				List.of(),
				null,
				null,
				null,
				null,
				null,
				null,
				"{\"source\":\"mock\",\"fileName\":\"" + name.replace("\"", "") + "\"}",
				null,
				type,
				null,
				null,
				null
		));
	}

	private static String guessParty(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		int dot = fileName.lastIndexOf('.');
		String base = dot > 0 ? fileName.substring(0, dot) : fileName;
		return base.replace('_', ' ').replace('-', ' ').trim();
	}
}
