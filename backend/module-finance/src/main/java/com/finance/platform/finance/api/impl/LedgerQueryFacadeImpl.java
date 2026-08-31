package com.finance.platform.finance.api.impl;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.api.LedgerQueryFacade;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerQueryFacadeImpl implements LedgerQueryFacade {

	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;

	@Override
	public List<ApprovedTransactionView> findApprovedTransactions(UUID clientId, LocalDate from, LocalDate to) {
		return findApprovedTransactions(SecurityUtils.requireCurrentUser().getFirmId(), clientId, from, to, null, null);
	}

	@Override
	public List<ApprovedTransactionView> findApprovedTransactions(
			UUID firmId,
			UUID clientId,
			LocalDate from,
			LocalDate to,
			String transactionType,
			UUID categoryId
	) {
		List<ApprovedTransactionView> rows = new ArrayList<>();
		boolean includeExpenses = transactionType == null || "EXPENSE".equalsIgnoreCase(transactionType);
		boolean includeIncome = transactionType == null || "INCOME".equalsIgnoreCase(transactionType);
		if (includeExpenses) {
			for (Expense expense : expenseRepository.findApprovedForReport(firmId, clientId, from, to, categoryId)) {
				rows.add(new ApprovedTransactionView(
						expense.getId(),
						"EXPENSE",
						expense.getTransactionDate(),
						expense.getAmount(),
						expense.getTaxAmount(),
						expense.getCurrencyCode(),
						expense.getVendorName(),
						null,
						expense.getCategory() != null ? expense.getCategory().getId() : null,
						expense.getCategory() != null ? expense.getCategory().getCode() : null,
						expense.getCategory() != null ? expense.getCategory().getName() : null
				));
			}
		}
		if (includeIncome) {
			for (Income income : incomeRepository.findApprovedForReport(firmId, clientId, from, to, categoryId)) {
				rows.add(new ApprovedTransactionView(
						income.getId(),
						"INCOME",
						income.getTransactionDate(),
						income.getAmount(),
						income.getTaxAmount(),
						income.getCurrencyCode(),
						income.getCustomerName(),
						income.getPaymentMethod() != null ? income.getPaymentMethod().name() : null,
						income.getCategory() != null ? income.getCategory().getId() : null,
						income.getCategory() != null ? income.getCategory().getCode() : null,
						income.getCategory() != null ? income.getCategory().getName() : null
				));
			}
		}
		rows.sort(Comparator.comparing(ApprovedTransactionView::transactionDate)
				.thenComparing(ApprovedTransactionView::type));
		return rows;
	}
}
