package com.finance.platform.finance.application.dto;

public record CreateBankAccountRequest(
		String bankName,
		String accountName,
		String maskedAccountNumber,
		String currency
) {
}
