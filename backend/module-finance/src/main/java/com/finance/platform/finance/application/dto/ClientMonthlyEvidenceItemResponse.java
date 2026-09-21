package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.ClientMonthlyEvidenceItem;
import com.finance.platform.finance.domain.model.Receipt;

import java.util.UUID;

public record ClientMonthlyEvidenceItemResponse(
		UUID id,
		String title,
		String description,
		Receipt.DocumentType documentType,
		boolean required,
		ClientMonthlyEvidenceItem.ResponsibleParty responsibleParty,
		boolean active,
		int sortOrder
) {
}
