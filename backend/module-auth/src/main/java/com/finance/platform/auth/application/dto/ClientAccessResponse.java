package com.finance.platform.auth.application.dto;

import java.util.UUID;

public record ClientAccessResponse(
		UUID clientId,
		String accessType
) {
}
