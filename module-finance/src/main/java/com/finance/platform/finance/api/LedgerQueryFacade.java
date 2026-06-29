package com.finance.platform.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerQueryFacade {

	List<ApprovedTransactionView> findApprovedTransactions(UUID clientId, LocalDate from, LocalDate to);

	record ApprovedTransactionView(
			UUID id,
			String type,
			LocalDate transactionDate,
			BigDecimal amount,
			String partyName,
			UUID categoryId
	) {
	}
}
