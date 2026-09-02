package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.bankimport.ImportPreview;
import com.finance.platform.finance.application.bankimport.ImportPreviewRow;
import com.finance.platform.finance.application.bankimport.ImportRowError;

import java.time.LocalDate;
import java.util.List;

public record BankImportPreviewResponse(
		int rowsDetected,
		int validRows,
		int invalidRows,
		int potentialDuplicates,
		LocalDate periodFrom,
		LocalDate periodTo,
		List<ImportPreviewRow> sampleRows,
		List<ImportRowError> errors
) {
	public static BankImportPreviewResponse from(ImportPreview preview) {
		return new BankImportPreviewResponse(
				preview.rowsDetected(),
				preview.validRows(),
				preview.invalidRows(),
				preview.potentialDuplicates(),
				preview.periodFrom(),
				preview.periodTo(),
				preview.sampleRows(),
				preview.errors());
	}
}
