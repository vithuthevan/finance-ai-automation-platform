package com.finance.platform.finance.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkDocumentRequest(
		@NotNull UUID documentId
) {
}
