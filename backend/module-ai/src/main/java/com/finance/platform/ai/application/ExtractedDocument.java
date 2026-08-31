package com.finance.platform.ai.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedDocument(
		String documentType,
		String supplierName,
		String customerName,
		String invoiceNumber,
		String receiptNumber,
		LocalDate documentDate,
		LocalDate dueDate,
		String currency,
		BigDecimal subtotal,
		BigDecimal taxAmount,
		BigDecimal discountAmount,
		BigDecimal totalAmount,
		String paymentMethod,
		String description,
		List<LineItem> lineItems,
		BigDecimal supplierConfidence,
		BigDecimal dateConfidence,
		BigDecimal amountConfidence,
		BigDecimal taxConfidence,
		BigDecimal overallConfidence,
		String ocrText,
		String rawJson,
		String candidateCategoryCode,
		String suggestedTransactionType,
		Integer inputTokens,
		Integer outputTokens,
		String failureCode
) {
	public record LineItem(String description, BigDecimal amount, BigDecimal quantity) {
	}

	public String partyName() {
		if (supplierName != null && !supplierName.isBlank()) {
			return supplierName;
		}
		return customerName;
	}
}
