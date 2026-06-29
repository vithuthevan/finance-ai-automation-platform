package com.finance.platform.finance.application.dto;

import java.util.UUID;

public record ClientResponse(
		UUID id,
		UUID firmId,
		String name,
		String businessRegNo,
		String contactEmail,
		boolean active
) {
}
