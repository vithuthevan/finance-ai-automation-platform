package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.BankTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransactionResponse(
		UUID id,
		UUID clientId,
		UUID importId,
		LocalDate txnDate,
		String description,
		String referenceNo,
		BigDecimal debit,
		BigDecimal credit,
		BigDecimal balance,
		BankTransaction.MatchStatus matchStatus
) {
	public static BankTransactionResponse from(BankTransaction txn) {
		return new BankTransactionResponse(
				txn.getId(),
				txn.getClient() != null ? txn.getClient().getId() : null,
				txn.getBankImport() != null ? txn.getBankImport().getId() : null,
				txn.getTxnDate(),
				txn.getDescription(),
				txn.getReferenceNo(),
				txn.getDebit(),
				txn.getCredit(),
				txn.getBalance(),
				txn.getMatchStatus()
		);
	}
}
