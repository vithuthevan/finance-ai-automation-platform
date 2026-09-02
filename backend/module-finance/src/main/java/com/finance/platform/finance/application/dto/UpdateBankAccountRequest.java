package com.finance.platform.finance.application.dto;

public record UpdateBankAccountRequest(
		String bankName,
		String accountName,
		String maskedAccountNumber,
		String currency,
		Boolean active
) {
}
