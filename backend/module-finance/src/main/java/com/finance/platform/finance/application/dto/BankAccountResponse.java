package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.BankAccount;

import java.time.Instant;
import java.util.UUID;

public record BankAccountResponse(
		UUID id,
		UUID clientId,
		String bankName,
		String accountName,
		String maskedAccountNumber,
		String currency,
		boolean active,
		Instant createdAt
) {
	public static BankAccountResponse from(BankAccount account) {
		return new BankAccountResponse(
				account.getId(),
				account.getClient().getId(),
				account.getBankName(),
				account.getAccountName(),
				account.displayAccountNumber(),
				account.getCurrency(),
				account.isActive(),
				account.getCreatedAt());
	}
}
