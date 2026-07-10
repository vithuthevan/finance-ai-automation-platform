package com.finance.platform.finance.application.mapper;

import com.finance.platform.finance.application.dto.ExpenseResponse;
import com.finance.platform.finance.application.dto.IncomeResponse;
import com.finance.platform.finance.domain.model.Expense;
import com.finance.platform.finance.domain.model.Income;

public final class FinanceMapper {

	private FinanceMapper() {
	}

	public static ExpenseResponse toExpenseResponse(Expense expense) {
		return new ExpenseResponse(
				expense.getId(),
				expense.getClient().getId(),
				expense.getCategory().getId(),
				expense.getTransactionDate(),
				expense.getAmount(),
				expense.getCurrencyCode(),
				expense.getVendorName(),
				expense.getDescription(),
				expense.getTaxAmount(),
				expense.getReferenceNo(),
				expense.getStatus().name(),
				expense.getSource().name(),
				expense.getApprovedAt(),
				expense.getCreatedAt(),
				expense.getUpdatedAt()
		);
	}

	public static IncomeResponse toIncomeResponse(Income income) {
		return new IncomeResponse(
				income.getId(),
				income.getClient().getId(),
				income.getCategory().getId(),
				income.getTransactionDate(),
				income.getAmount(),
				income.getCurrencyCode(),
				income.getCustomerName(),
				income.getDescription(),
				income.getPaymentMethod() != null ? income.getPaymentMethod().name() : null,
				income.getTaxAmount(),
				income.getReferenceNo(),
				income.getStatus().name(),
				income.getSource().name(),
				income.getCreatedByUser() != null ? income.getCreatedByUser().getId() : null,
				income.getApprovedAt(),
				income.getCreatedAt(),
				income.getUpdatedAt()
		);
	}
}
