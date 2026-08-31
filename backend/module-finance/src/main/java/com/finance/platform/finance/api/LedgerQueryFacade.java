package com.finance.platform.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerQueryFacade {

	List<ApprovedTransactionView> findApprovedTransactions(UUID clientId, LocalDate from, LocalDate to);

	List<ApprovedTransactionView> findApprovedTransactions(
			UUID firmId,
			UUID clientId,
			LocalDate from,
			LocalDate to,
			String transactionType,
			UUID categoryId
	);

	record ApprovedTransactionView(
			UUID id,
			String type,
			LocalDate transactionDate,
			BigDecimal amount,
			BigDecimal taxAmount,
			String currencyCode,
			String partyName,
			String paymentMethod,
			UUID categoryId,
			String categoryCode,
			String categoryName
	) {
	}
}
