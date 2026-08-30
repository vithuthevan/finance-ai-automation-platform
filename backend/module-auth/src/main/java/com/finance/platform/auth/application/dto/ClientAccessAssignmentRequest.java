package com.finance.platform.auth.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClientAccessAssignmentRequest(
		@NotNull UUID clientId,
		String accessType
) {
}
