package com.finance.platform.finance.application.bankimport;

import java.time.LocalDate;
import java.util.List;

public record ImportPreview(
		int rowsDetected,
		int validRows,
		int invalidRows,
		int potentialDuplicates,
		LocalDate periodFrom,
		LocalDate periodTo,
		List<ImportPreviewRow> sampleRows,
		List<ImportRowError> errors
) {
}
