package com.finance.platform.finance.api.impl;

import com.finance.platform.finance.api.LedgerQueryFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LedgerQueryFacadeImpl implements LedgerQueryFacade {

	@Override
	public List<ApprovedTransactionView> findApprovedTransactions(UUID clientId, LocalDate from, LocalDate to) {
		// TODO: query approved expenses and income
		return Collections.emptyList();
	}
}
