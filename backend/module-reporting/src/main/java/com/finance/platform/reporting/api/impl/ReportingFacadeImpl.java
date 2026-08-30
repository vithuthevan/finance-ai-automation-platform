package com.finance.platform.reporting.api.impl;

import com.finance.platform.finance.api.LedgerQueryFacade;
import com.finance.platform.finance.application.service.ClientAccessService;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import com.finance.platform.reporting.api.ReportingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingFacadeImpl implements ReportingFacade {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private final LedgerQueryFacade ledgerQueryFacade;
	private final ClientAccessService clientAccessService;
	private final ReceiptJpaRepository receiptRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;

	@Override
	@Transactional(readOnly = true)
	public PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to) {
		clientAccessService.requireReportAccess(clientId);
		validateRange(from, to);
		List<LedgerQueryFacade.ApprovedTransactionView> rows = ledgerQueryFacade.findApprovedTransactions(clientId, from, to);
		return toSummary(clientId, from, to, rows);
	}

	@Override
	@Transactional(readOnly = true)
	public PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to) {
		PlSummary current = generatePlSummary(clientId, from, to);
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		LocalDate previousTo = from.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(days - 1);
		PlSummary previous = generatePlSummary(clientId, previousFrom, previousTo);
		return new PlComparison(
				clientId,
				new PeriodTotals(from, to, current.totalIncome(), current.totalExpenses(), current.netResult()),
				new PeriodTotals(previousFrom, previousTo, previous.totalIncome(), previous.totalExpenses(), previous.netResult()),
				current.totalIncome().subtract(previous.totalIncome()),
				current.totalExpenses().subtract(previous.totalExpenses()),
				current.netResult().subtract(previous.netResult()),
				percentChange(previous.totalIncome(), current.totalIncome()),
				percentChange(previous.totalExpenses(), current.totalExpenses()),
				percentChange(previous.netResult(), current.netResult())
		);
	}

	@Override
	@Transactional(readOnly = true)
	public DashboardSummary generateDashboard(UUID clientId, LocalDate from, LocalDate to) {
		PlSummary pl = generatePlSummary(clientId, from, to);
		long unreviewed = receiptRepository.countByClientIdAndStatusInAndDeletedAtIsNull(
				clientId,
				List.of(Receipt.ReceiptStatus.UPLOADED, Receipt.ReceiptStatus.NEEDS_REVIEW, Receipt.ReceiptStatus.EXTRACTED, Receipt.ReceiptStatus.PROCESSING)
		);
		long drafts = expenseRepository.countByClientIdAndStatus(clientId, TransactionStatus.DRAFT)
				+ incomeRepository.countByClientIdAndStatus(clientId, TransactionStatus.DRAFT);
		long unreconciled = bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.UNMATCHED)
				+ bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.SUGGESTED);
		List<String> blockers = new ArrayList<>();
		if (unreviewed > 0) {
			blockers.add(unreviewed + " documents need review");
		}
		if (drafts > 0) {
			blockers.add(drafts + " draft transactions are unapproved");
		}
		if (unreconciled > 0) {
			blockers.add(unreconciled + " bank entries are unreconciled");
		}
		int readiness = blockers.isEmpty() ? 100 : Math.max(0, 100 - (int) Math.min(90, (unreviewed + drafts + unreconciled) * 8));
		return new DashboardSummary(
				clientId,
				pl.totalIncome(),
				pl.totalExpenses(),
				pl.netResult(),
				unreviewed,
				drafts,
				unreconciled,
				readiness,
				blockers
		);
	}

	private PlSummary toSummary(UUID clientId, LocalDate from, LocalDate to, List<LedgerQueryFacade.ApprovedTransactionView> rows) {
		BigDecimal income = BigDecimal.ZERO;
		BigDecimal expenses = BigDecimal.ZERO;
		Map<UUID, CategoryAmount> incomeCats = new LinkedHashMap<>();
		Map<UUID, CategoryAmount> expenseCats = new LinkedHashMap<>();
		for (LedgerQueryFacade.ApprovedTransactionView row : rows) {
			BigDecimal amount = row.amount() != null ? row.amount() : BigDecimal.ZERO;
			if ("INCOME".equals(row.type())) {
				income = income.add(amount);
				accumulate(incomeCats, row, amount);
			} else {
				expenses = expenses.add(amount);
				accumulate(expenseCats, row, amount);
			}
		}
		return new PlSummary(
				clientId,
				from,
				to,
				income,
				expenses,
				income.subtract(expenses),
				List.copyOf(incomeCats.values()),
				List.copyOf(expenseCats.values())
		);
	}

	private void accumulate(Map<UUID, CategoryAmount> target, LedgerQueryFacade.ApprovedTransactionView row, BigDecimal amount) {
		UUID key = row.categoryId() != null ? row.categoryId() : new UUID(0, 0);
		CategoryAmount existing = target.get(key);
		if (existing == null) {
			target.put(key, new CategoryAmount(row.categoryId(), row.categoryCode(), row.categoryName(), amount));
		} else {
			target.put(key, new CategoryAmount(existing.categoryId(), existing.categoryCode(), existing.categoryName(), existing.amount().add(amount)));
		}
	}

	private static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
		if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
			return current != null && current.compareTo(BigDecimal.ZERO) != 0 ? HUNDRED : BigDecimal.ZERO;
		}
		return current.subtract(previous).multiply(HUNDRED).divide(previous.abs(), 2, RoundingMode.HALF_UP);
	}

	private static void validateRange(LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			throw new com.finance.platform.core.exception.ValidationException("date range is invalid");
		}
	}
}
