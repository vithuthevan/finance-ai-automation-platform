package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.dto.invoicing.PaymentAllocationItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ConfirmBankInvoicePaymentRequest(
		@NotNull UUID customerId,
		List<@Valid PaymentAllocationItemRequest> allocations
) {
}
