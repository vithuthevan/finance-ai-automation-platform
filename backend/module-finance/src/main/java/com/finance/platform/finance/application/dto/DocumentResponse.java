package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.AiExtractionMetadata;
import com.finance.platform.finance.domain.model.Receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
		UUID id,
		UUID firmId,
		UUID clientId,
		String clientName,
		UUID uploadedByUserId,
		String uploadedByName,
		String description,
		String fileName,
		String mimeType,
		Long fileSizeBytes,
		String checksumSha256,
		Receipt.DocumentType documentType,
		Receipt.ReceiptStatus status,
		AiExtractionMetadata.ExtractionStatus extractionStatus,
		BigDecimal confidenceScore,
		String suggestedType,
		String suggestedVendorOrCustomer,
		LocalDate suggestedDate,
		BigDecimal suggestedAmount,
		UUID suggestedCategoryId,
		String ocrText,
		boolean possibleDuplicate,
		UUID existingDocumentId,
		List<UUID> linkedExpenseIds,
		List<UUID> linkedIncomeIds,
		UUID reviewedByUserId,
		Instant reviewedAt,
		String reviewNote,
		Instant uploadedAt,
		Instant createdAt
) {
}
