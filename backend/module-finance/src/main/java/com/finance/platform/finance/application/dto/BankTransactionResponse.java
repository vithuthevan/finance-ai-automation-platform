package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.ReconciliationMatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BankTransactionResponse(
		UUID id,
		UUID clientId,
		UUID bankAccountId,
		UUID importId,
		LocalDate txnDate,
		LocalDate valueDate,
		String description,
		String referenceNo,
		BigDecimal debit,
		BigDecimal credit,
		BigDecimal balance,
		String currency,
		BankTransaction.TransactionDirection direction,
		BankTransaction.MatchStatus matchStatus,
		String ignoreReason,
		UUID pendingExpenseId,
		UUID pendingIncomeId,
		List<MatchSuggestionResponse> suggestions
) {
	public static BankTransactionResponse from(BankTransaction txn) {
		return from(txn, List.of());
	}

	public static BankTransactionResponse from(BankTransaction txn, List<MatchSuggestionResponse> suggestions) {
		return new BankTransactionResponse(
				txn.getId(),
				txn.getClient() != null ? txn.getClient().getId() : null,
				txn.getBankAccount() != null ? txn.getBankAccount().getId() : null,
				txn.getBankImport() != null ? txn.getBankImport().getId() : null,
				txn.getTxnDate(),
				txn.getValueDate(),
				txn.getDescription(),
				txn.getReferenceNo(),
				txn.getDebit(),
				txn.getCredit(),
				txn.getBalance(),
				txn.getCurrency(),
				txn.getDirection(),
				txn.getMatchStatus(),
				txn.getIgnoreReason(),
				txn.getPendingExpenseId(),
				txn.getPendingIncomeId(),
				suggestions);
	}
}
