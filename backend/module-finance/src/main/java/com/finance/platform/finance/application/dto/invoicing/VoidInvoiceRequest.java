package com.finance.platform.finance.application.dto.invoicing;

import jakarta.validation.constraints.NotBlank;

public record VoidInvoiceRequest(@NotBlank String reason) {
}
