package com.finance.platform.finance.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDocumentRequestFromBankRequest(
		String description,
		String documentType,
		LocalDate dueDate,
		UUID periodId
) {
}
