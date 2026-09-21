package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.ClientMonthlyEvidenceItem;
import com.finance.platform.finance.domain.model.Receipt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertClientMonthlyEvidenceItemRequest(
		@NotBlank @Size(max = 200) String title,
		String description,
		Receipt.DocumentType documentType,
		Boolean required,
		ClientMonthlyEvidenceItem.ResponsibleParty responsibleParty,
		Boolean active,
		Integer sortOrder
) {
}
