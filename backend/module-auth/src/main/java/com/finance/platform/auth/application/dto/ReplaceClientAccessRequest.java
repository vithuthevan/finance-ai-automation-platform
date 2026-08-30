package com.finance.platform.auth.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceClientAccessRequest(
		@NotNull @Valid List<ClientAccessAssignmentRequest> assignments
) {
}
