package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.BankImport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankImportResponse(
		UUID id,
		UUID clientId,
		UUID bankAccountId,
		String fileName,
		String checksum,
		LocalDate periodFrom,
		LocalDate periodTo,
		BankImport.ImportStatus importStatus,
		int rowCount,
		int importedCount,
		int duplicateCount,
		int failedCount,
		String errorMessage,
		Instant createdAt,
		Instant completedAt
) {
	public static BankImportResponse from(BankImport batch) {
		return new BankImportResponse(
				batch.getId(),
				batch.getClient().getId(),
				batch.getBankAccount() == null ? null : batch.getBankAccount().getId(),
				batch.getFileName(),
				batch.getChecksum(),
				batch.getPeriodFrom(),
				batch.getPeriodTo(),
				batch.getImportStatus(),
				batch.getRowCount(),
				batch.getImportedCount(),
				batch.getDuplicateCount(),
				batch.getFailedCount(),
				batch.getErrorMessage(),
				batch.getCreatedAt(),
				batch.getCompletedAt());
	}
}
