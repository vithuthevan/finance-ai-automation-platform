package com.finance.platform.finance.application.dto.invoicing;

import java.util.UUID;

public record ArCustomerResponse(
		UUID id,
		String name,
		String email,
		UUID clientId,
		int paymentTermsDays,
		boolean active
) {
}
