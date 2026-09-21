package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AllocatePaymentRequest(
		@NotEmpty List<@Valid PaymentAllocationItemRequest> allocations
) {
}
