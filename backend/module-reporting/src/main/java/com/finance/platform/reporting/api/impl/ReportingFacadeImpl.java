package com.finance.platform.reporting.api.impl;

import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.service.ClientAccessService;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.Firm;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.FirmJpaRepository;
import com.finance.platform.reporting.api.ReportingFacade;
import com.finance.platform.reporting.infrastructure.ReportingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingFacadeImpl implements ReportingFacade {

	private static final int MONEY_SCALE = 2;
	private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
	private static final int MAX_PERIOD_YEARS = 10;
	private static final int MAX_TREND_MONTHS = 36;
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final String CASH_DISCLAIMER =
			"Net recorded movement of approved income minus approved expenses. This is not an IFRS cash-flow statement.";

	private final ClientAccessService clientAccessService;
	private final FirmJpaRepository firmRepository;
	private final ReportingQueryRepository reportingQueryRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;

	@Override
	@Transactional(readOnly = true)
	public PlSummary generatePlSummary(UUID clientId, LocalDate from, LocalDate to) {
		Client client = requireReportClient(clientId);
		validateRange(from, to, MAX_PERIOD_YEARS * 366L);
		UUID firmId = client.getFirmId();
		BigDecimal income = money(reportingQueryRepository.sumApproved(firmId, clientId, "INCOME", from, to));
		BigDecimal expenses = money(reportingQueryRepository.sumApproved(firmId, clientId, "EXPENSE", from, to));
		BigDecimal net = money(income.subtract(expenses));
		long count = reportingQueryRepository.countApproved(firmId, clientId, "INCOME", from, to)
				+ reportingQueryRepository.countApproved(firmId, clientId, "EXPENSE", from, to);
		return new PlSummary(
				client.getId(),
				client.getName(),
				from,
				to,
				currencyOf(firmId),
				count > 0,
				resultType(net),
				income,
				expenses,
				net,
				withPercentages(reportingQueryRepository.categoryTotals(firmId, clientId, "INCOME", from, to), income),
				withPercentages(reportingQueryRepository.categoryTotals(firmId, clientId, "EXPENSE", from, to), expenses)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to) {
		validateRange(from, to, MAX_PERIOD_YEARS * 366L);
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		LocalDate previousTo = from.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(days - 1);
		return generatePlComparison(clientId, from, to, previousFrom, previousTo);
	}

	@Override
	@Transactional(readOnly = true)
	public PlComparison generatePlComparison(UUID clientId, LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo) {
		PlSummary current = generatePlSummary(clientId, from, to);
		PlSummary previous = generatePlSummary(clientId, compareFrom, compareTo);
		return new PlComparison(
				current.clientId(),
				current.clientName(),
				current.currencyCode(),
				new PeriodTotals(from, to, current.totalIncome(), current.totalExpenses(), current.netResult(), current.resultType()),
				new PeriodTotals(compareFrom, compareTo, previous.totalIncome(), previous.totalExpenses(), previous.netResult(), previous.resultType()),
				money(current.totalIncome().subtract(previous.totalIncome())),
				money(current.totalExpenses().subtract(previous.totalExpenses())),
				money(current.netResult().subtract(previous.netResult())),
				percentChange(previous.totalIncome(), current.totalIncome()),
				percentChange(previous.totalExpenses(), current.totalExpenses()),
				percentChange(previous.netResult(), current.netResult())
		);
	}

	@Override
	@Transactional(readOnly = true)
	public DashboardSummary generateDashboard(UUID clientId, LocalDate from, LocalDate to) {
		PlSummary pl = generatePlSummary(clientId, from, to);
		DocumentSupportSummary documents = generateDocumentSupport(clientId, from, to);
		TransactionStatusSummary statuses = generateStatusSummary(clientId);
		long drafts = statuses.draftExpenses() + statuses.draftIncome();
		long unreconciled = bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.UNMATCHED)
				+ bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.SUGGESTED);
		List<String> blockers = new ArrayList<>();
		if (documents.documentsAwaitingReview() > 0) {
			blockers.add(documents.documentsAwaitingReview() + " documents need review");
		}
		if (drafts > 0) {
			blockers.add(drafts + " draft transactions are unapproved");
		}
		if (unreconciled > 0) {
			blockers.add(unreconciled + " bank entries are unreconciled");
		}
		int readiness = blockers.isEmpty() ? 100 : Math.max(0, 100 - (int) Math.min(90, (documents.documentsAwaitingReview() + drafts + unreconciled) * 8));
		return new DashboardSummary(
				pl.clientId(),
				pl.clientName(),
				pl.currencyCode(),
				pl.hasApprovedData(),
				pl.totalIncome(),
				pl.totalExpenses(),
				pl.netResult(),
				pl.resultType(),
				documents.documentsAwaitingReview(),
				drafts,
				unreconciled,
				readiness,
				blockers,
				documents.unlinkedDocuments(),
				documents.approvedWithDocuments(),
				documents.approvedWithoutDocuments(),
				statuses
		);
	}

	@Override
	@Transactional(readOnly = true)
	public IncomeSummary generateIncomeSummary(UUID clientId, LocalDate from, LocalDate to) {
		Client client = requireReportClient(clientId);
		validateRange(from, to, MAX_PERIOD_YEARS * 366L);
		UUID firmId = client.getFirmId();
		BigDecimal total = money(reportingQueryRepository.sumApproved(firmId, clientId, "INCOME", from, to));
		long count = reportingQueryRepository.countApproved(firmId, clientId, "INCOME", from, to);
		List<NamedAmount> byPayment = reportingQueryRepository.incomeByPaymentMethod(firmId, clientId, from, to).stream()
				.map(row -> new NamedAmount(row.name(), money(row.amount()), row.count(), percentOf(row.amount(), total)))
				.toList();
		return new IncomeSummary(
				client.getId(),
				client.getName(),
				from,
				to,
				currencyOf(firmId),
				count > 0,
				total,
				count,
				count == 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING) : money(total.divide(BigDecimal.valueOf(count), MONEY_SCALE, MONEY_ROUNDING)),
				withPercentages(reportingQueryRepository.categoryTotals(firmId, clientId, "INCOME", from, to), total),
				byPayment
		);
	}

	@Override
	@Transactional(readOnly = true)
	public ExpenseSummary generateExpenseSummary(UUID clientId, LocalDate from, LocalDate to) {
		Client client = requireReportClient(clientId);
		validateRange(from, to, MAX_PERIOD_YEARS * 366L);
		UUID firmId = client.getFirmId();
		BigDecimal total = money(reportingQueryRepository.sumApproved(firmId, clientId, "EXPENSE", from, to));
		long count = reportingQueryRepository.countApproved(firmId, clientId, "EXPENSE", from, to);
		List<LargestItem> largest = reportingQueryRepository.largestApproved(firmId, clientId, "EXPENSE", from, to, 5).stream()
				.map(row -> new LargestItem(row.id(), row.transactionDate(), money(row.amount()), row.partyName(), row.categoryName()))
				.toList();
		return new ExpenseSummary(
				client.getId(),
				client.getName(),
				from,
				to,
				currencyOf(firmId),
				count > 0,
				total,
				count,
				count == 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING) : money(total.divide(BigDecimal.valueOf(count), MONEY_SCALE, MONEY_ROUNDING)),
				withPercentages(reportingQueryRepository.categoryTotals(firmId, clientId, "EXPENSE", from, to), total),
				largest
		);
	}

	@Override
	@Transactional(readOnly = true)
	public CashMovementSummary generateCashMovement(UUID clientId, LocalDate from, LocalDate to) {
		PlSummary pl = generatePlSummary(clientId, from, to);
		return new CashMovementSummary(
				pl.clientId(),
				pl.clientName(),
				from,
				to,
				pl.currencyCode(),
				pl.hasApprovedData(),
				pl.totalIncome(),
				pl.totalExpenses(),
				pl.netResult(),
				CASH_DISCLAIMER
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MonthlyTrend> generateMonthlyTrend(UUID clientId, LocalDate from, LocalDate to) {
		Client client = requireReportClient(clientId);
		validateRange(from, to, MAX_TREND_MONTHS * 31L);
		UUID firmId = client.getFirmId();
		Map<LocalDate, BigDecimal> income = toMonthMap(reportingQueryRepository.monthlyTotals(firmId, clientId, "INCOME", from, to));
		Map<LocalDate, BigDecimal> expenses = toMonthMap(reportingQueryRepository.monthlyTotals(firmId, clientId, "EXPENSE", from, to));
		List<MonthlyTrend> rows = new ArrayList<>();
		LocalDate cursor = from.withDayOfMonth(1);
		LocalDate last = to.withDayOfMonth(1);
		while (!cursor.isAfter(last)) {
			BigDecimal monthIncome = money(income.getOrDefault(cursor, BigDecimal.ZERO));
			BigDecimal monthExpense = money(expenses.getOrDefault(cursor, BigDecimal.ZERO));
			rows.add(new MonthlyTrend(cursor, monthIncome, monthExpense, money(monthIncome.subtract(monthExpense))));
			cursor = cursor.plusMonths(1);
		}
		return rows;
	}

	@Override
	@Transactional(readOnly = true)
	public TopCategories generateTopCategories(UUID clientId, LocalDate from, LocalDate to) {
		PlSummary pl = generatePlSummary(clientId, from, to);
		return new TopCategories(limit(pl.incomeByCategory(), 5), limit(pl.expensesByCategory(), 5));
	}

	@Override
	@Transactional(readOnly = true)
	public TransactionStatusSummary generateStatusSummary(UUID clientId) {
		Client client = requireReportClient(clientId);
		Map<String, Long> expenses = reportingQueryRepository.statusCounts(client.getFirmId(), clientId, "EXPENSE");
		Map<String, Long> income = reportingQueryRepository.statusCounts(client.getFirmId(), clientId, "INCOME");
		return new TransactionStatusSummary(
				expenses.getOrDefault(TransactionStatus.DRAFT.name(), 0L),
				expenses.getOrDefault(TransactionStatus.APPROVED.name(), 0L),
				expenses.getOrDefault(TransactionStatus.VOID.name(), 0L),
				income.getOrDefault(TransactionStatus.DRAFT.name(), 0L),
				income.getOrDefault(TransactionStatus.APPROVED.name(), 0L),
				income.getOrDefault(TransactionStatus.VOID.name(), 0L)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public DocumentSupportSummary generateDocumentSupport(UUID clientId, LocalDate from, LocalDate to) {
		Client client = requireReportClient(clientId);
		validateRange(from, to, MAX_PERIOD_YEARS * 366L);
		ReportingQueryRepository.DocumentSupportCounts counts =
				reportingQueryRepository.documentSupport(client.getFirmId(), clientId, from, to);
		return new DocumentSupportSummary(
				counts.approvedWithDocuments(),
				counts.approvedWithoutDocuments(),
				counts.unlinkedDocuments(),
				counts.awaitingReview()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public PracticeDashboard generatePracticeDashboard() {
		UUID firmId = SecurityUtils.requireCurrentUser().getFirmId();
		boolean admin = clientAccessService.isAdmin();
		Set<UUID> clientIds = admin ? Set.of() : clientAccessService.accessibleClientIds();
		ReportingQueryRepository.PracticeCounts counts = reportingQueryRepository.practiceCounts(firmId, clientIds, admin);
		return new PracticeDashboard(
				counts.activeClients(),
				counts.clientsWithDrafts(),
				counts.documentsNeedingReview(),
				counts.pendingApprovals(),
				counts.clientsWithRecentActivity()
		);
	}

	private Client requireReportClient(UUID clientId) {
		return clientAccessService.requireReportAccess(clientId);
	}

	private String currencyOf(UUID firmId) {
		return firmRepository.findById(firmId).map(Firm::getCurrencyCode).orElse("LKR");
	}

	private static List<CategoryAmount> withPercentages(List<ReportingQueryRepository.CategoryTotal> rows, BigDecimal total) {
		return rows.stream()
				.map(row -> new CategoryAmount(
						row.categoryId(),
						row.categoryCode(),
						row.categoryName(),
						money(row.amount()),
						percentOf(row.amount(), total),
						row.parentId(),
						row.parentName()))
				.toList();
	}

	private static List<CategoryAmount> limit(List<CategoryAmount> rows, int size) {
		if (rows.size() <= size) {
			return rows;
		}
		return List.copyOf(rows.subList(0, size));
	}

	private static Map<LocalDate, BigDecimal> toMonthMap(List<ReportingQueryRepository.MonthlyTotal> rows) {
		Map<LocalDate, BigDecimal> map = new java.util.LinkedHashMap<>();
		for (ReportingQueryRepository.MonthlyTotal row : rows) {
			map.put(row.monthStart(), row.amount());
		}
		return map;
	}

	private static BigDecimal percentOf(BigDecimal part, BigDecimal total) {
		if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
		}
		return money(part.multiply(HUNDRED).divide(total, MONEY_SCALE, MONEY_ROUNDING));
	}

	private static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
		if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return current.subtract(previous).multiply(HUNDRED).divide(previous.abs(), MONEY_SCALE, MONEY_ROUNDING);
	}

	private static String resultType(BigDecimal net) {
		int sign = net.compareTo(BigDecimal.ZERO);
		if (sign > 0) {
			return "PROFIT";
		}
		if (sign < 0) {
			return "LOSS";
		}
		return "BREAK_EVEN";
	}

	private static BigDecimal money(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
	}

	private static void validateRange(LocalDate from, LocalDate to, long maxDays) {
		if (from == null || to == null) {
			throw new ValidationException(ErrorCodes.INVALID_REPORT_PERIOD, "from", "Report period dates are required");
		}
		if (to.isBefore(from)) {
			throw new ValidationException(ErrorCodes.INVALID_REPORT_PERIOD, "to", "Period start must be on or before period end");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
			throw new ValidationException(ErrorCodes.INVALID_REPORT_PERIOD, "to", "Report period is too long");
		}
	}
}
