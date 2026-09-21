package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ArCustomerRequest(
		@NotBlank String name,
		@Email String email,
		UUID clientId,
		@PositiveOrZero int paymentTermsDays
) {
}
