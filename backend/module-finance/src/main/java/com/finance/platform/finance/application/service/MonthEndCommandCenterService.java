package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.finance.application.close.CloseActionLinks;
import com.finance.platform.finance.application.close.CloseCheckSeverity;
import com.finance.platform.finance.application.dto.CloseActionLinkResponse;
import com.finance.platform.finance.application.dto.CloseFindingResponse;
import com.finance.platform.finance.application.dto.MonthEndBlockerResponse;
import com.finance.platform.finance.application.dto.MonthEndClientRowResponse;
import com.finance.platform.finance.application.dto.MonthEndCommandCenterResponse;
import com.finance.platform.finance.application.dto.MonthEndCommandCenterSummaryResponse;
import com.finance.platform.finance.application.dto.MonthEndPortfolioState;
import com.finance.platform.finance.application.dto.MonthEndProgressStepResponse;
import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.dto.PeriodReadinessSummaryResponse;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.PracticeWorkQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonthEndCommandCenterService {

	private final ClientJpaRepository clientRepository;
	private final ClientAccessService clientAccessService;
	private final UserJpaRepository userRepository;
	private final AccountingPeriodJpaRepository periodRepository;
	private final CloseReadinessService closeReadinessService;
	private final PracticeWorkQueryRepository workQueries;

	@Transactional(readOnly = true)
	public MonthEndCommandCenterResponse commandCenter(
			Integer year,
			Integer month,
			String query,
			MonthEndPortfolioState stateFilter,
			UUID accountantUserId,
			UUID clientId
	) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		assertPracticeRole(user);
		YearMonth period = resolvePeriod(year, month);
		LocalDate from = period.atDay(1);
		LocalDate to = period.atEndOfMonth();
		UUID firmId = user.getFirmId();

		List<Client> clients = accessibleClients(firmId, query);
		if (clientId != null) {
			clients = clients.stream().filter(c -> c.getId().equals(clientId)).toList();
		}
		if (accountantUserId != null) {
			clients = clients.stream()
					.filter(client -> accountantUserId.equals(client.getPrimaryAccountantUserId()))
					.toList();
		}

		List<MonthEndClientRowResponse> rows = new ArrayList<>();
		int ready = 0;
		int attention = 0;
		int blocked = 0;
		int closed = 0;
		long overdueTotal = 0;
		long openRequestsTotal = 0;

		for (Client client : clients) {
			AccountingPeriod accountingPeriod = periodRepository
					.findByClient_IdAndPeriodYearAndPeriodMonth(client.getId(), period.getYear(), period.getMonthValue())
					.orElse(null);
			UUID periodId = accountingPeriod == null ? null : accountingPeriod.getId();
			LocalDate evalFrom = accountingPeriod == null ? from : accountingPeriod.getStartDate();
			LocalDate evalTo = accountingPeriod == null ? to : accountingPeriod.getEndDate();

			PeriodReadinessResponse readiness = closeReadinessService.evaluate(
					firmId, client.getId(), periodId, evalFrom, evalTo);
			AccountingPeriod.PeriodStatus persisted = accountingPeriod == null
					? AccountingPeriod.PeriodStatus.OPEN
					: accountingPeriod.getStatus();

			long overdue = workQueries.countOverdueDocumentRequests(firmId, Set.of(client.getId()), false);
			long openRequests = readiness.summary().openDocumentRequests();
			overdueTotal += overdue;
			openRequestsTotal += openRequests;

			MonthEndPortfolioState state = resolveState(persisted, readiness, overdue);
			switch (state) {
				case CLOSED -> closed++;
				case READY -> ready++;
				case ATTENTION -> attention++;
				case BLOCKED -> blocked++;
			}

			if (stateFilter != null && state != stateFilter) {
				continue;
			}

			List<MonthEndBlockerResponse> blockers = mapBlockers(
					readiness, client.getId(), periodId, evalFrom, evalTo);
			CloseActionLinkResponse primaryAction = primaryAction(state, blockers, client.getId(), periodId);

			String accountantName = null;
			if (client.getPrimaryAccountantUserId() != null) {
				accountantName = userRepository.findById(client.getPrimaryAccountantUserId())
						.map(User::getFullName)
						.orElse(null);
			}

			rows.add(new MonthEndClientRowResponse(
					client.getId(),
					client.getName(),
					periodId,
					period.getYear(),
					period.getMonthValue(),
					evalFrom,
					evalTo,
					state,
					state.name(),
					readiness.ready() && persisted != AccountingPeriod.PeriodStatus.CLOSED,
					readiness.readinessPercent(),
					overdue,
					client.getPrimaryAccountantUserId(),
					accountantName,
					buildProgress(readiness, persisted),
					blockers,
					primaryAction
			));
		}

		rows.sort(Comparator
				.comparingInt((MonthEndClientRowResponse row) -> stateRank(row.state()))
				.thenComparing(MonthEndClientRowResponse::clientName, String.CASE_INSENSITIVE_ORDER));

		String label = period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
				+ " " + period.getYear() + " month-end";

		return new MonthEndCommandCenterResponse(
				period.getYear(),
				period.getMonthValue(),
				label,
				period,
				new MonthEndCommandCenterSummaryResponse(
						clients.size(),
						ready,
						attention,
						blocked,
						closed,
						overdueTotal,
						openRequestsTotal
				),
				rows
		);
	}

	private static MonthEndPortfolioState resolveState(
			AccountingPeriod.PeriodStatus persisted,
			PeriodReadinessResponse readiness,
			long overdueRequests
	) {
		if (persisted == AccountingPeriod.PeriodStatus.CLOSED) {
			return MonthEndPortfolioState.CLOSED;
		}
		if (!readiness.ready()) {
			return MonthEndPortfolioState.BLOCKED;
		}
		if (!readiness.warnings().isEmpty() || overdueRequests > 0) {
			return MonthEndPortfolioState.ATTENTION;
		}
		return MonthEndPortfolioState.READY;
	}

	private static List<MonthEndBlockerResponse> mapBlockers(
			PeriodReadinessResponse readiness,
			UUID clientId,
			UUID periodId,
			LocalDate from,
			LocalDate to
	) {
		List<MonthEndBlockerResponse> items = new ArrayList<>();
		for (CloseFindingResponse blocker : readiness.blockers()) {
			items.add(toBlocker(blocker, clientId, periodId, from, to));
		}
		for (CloseFindingResponse warning : readiness.warnings()) {
			items.add(toBlocker(warning, clientId, periodId, from, to));
		}
		return items;
	}

	private static MonthEndBlockerResponse toBlocker(
			CloseFindingResponse finding,
			UUID clientId,
			UUID periodId,
			LocalDate from,
			LocalDate to
	) {
		CloseActionLinkResponse action = CloseActionLinks.resolve(
				finding.actionHint(), clientId, periodId, from, to);
		return new MonthEndBlockerResponse(
				finding.severity(),
				finding.code(),
				finding.message(),
				finding.count(),
				finding.actionHint(),
				action
		);
	}

	private static CloseActionLinkResponse primaryAction(
			MonthEndPortfolioState state,
			List<MonthEndBlockerResponse> blockers,
			UUID clientId,
			UUID periodId
	) {
		if (state == MonthEndPortfolioState.CLOSED) {
			return periodId != null
					? new CloseActionLinkResponse("View closed period", "/app/close/" + clientId + "/" + periodId, java.util.Map.of())
					: new CloseActionLinkResponse("View close", "/app/close", java.util.Map.of());
		}
		if (state == MonthEndPortfolioState.READY) {
			return periodId != null
					? new CloseActionLinkResponse("Review & close", "/app/close/" + clientId + "/" + periodId, java.util.Map.of())
					: new CloseActionLinkResponse("Prepare close", "/app/close", java.util.Map.of("clientId", clientId.toString()));
		}
		MonthEndBlockerResponse firstBlocker = blockers.stream()
				.filter(b -> b.severity() == CloseCheckSeverity.BLOCKER)
				.findFirst()
				.orElse(blockers.isEmpty() ? null : blockers.get(0));
		if (firstBlocker != null) {
			return firstBlocker.action();
		}
		return new CloseActionLinkResponse("Resolve blockers", "/app/close", java.util.Map.of("clientId", clientId.toString()));
	}

	private static List<MonthEndProgressStepResponse> buildProgress(
			PeriodReadinessResponse readiness,
			AccountingPeriod.PeriodStatus persisted
	) {
		PeriodReadinessSummaryResponse s = readiness.summary();
		List<MonthEndProgressStepResponse> steps = new ArrayList<>();
		steps.add(new MonthEndProgressStepResponse(
				"DOCUMENTS",
				"Documents",
				s.documentsNeedingReview() > 0 || s.failedUnlinkedDocuments() > 0 ? "!" : "✓"));
		long drafts = s.draftExpenses() + s.draftIncome();
		steps.add(new MonthEndProgressStepResponse(
				"APPROVALS",
				"Approvals",
				drafts > 0 ? "✕" : "✓"));
		String bankIndicator;
		if (s.bankAccounts() == 0) {
			bankIndicator = "—";
		} else if (s.unmatchedBankTransactions() > 0) {
			bankIndicator = "!";
		} else if (s.bankTransactions() == 0) {
			bankIndicator = "!";
		} else {
			bankIndicator = "✓";
		}
		steps.add(new MonthEndProgressStepResponse("BANK", "Bank", bankIndicator));
		steps.add(new MonthEndProgressStepResponse(
				"REQUESTS",
				"Requests",
				s.openDocumentRequests() > 0 ? "✕" : "✓"));
		String closeIndicator = persisted == AccountingPeriod.PeriodStatus.CLOSED
				? "✓"
				: (readiness.ready() ? "→" : "—");
		steps.add(new MonthEndProgressStepResponse("CLOSE", "Close", closeIndicator));
		return steps;
	}

	private static int stateRank(MonthEndPortfolioState state) {
		return switch (state) {
			case BLOCKED -> 0;
			case ATTENTION -> 1;
			case READY -> 2;
			case CLOSED -> 3;
		};
	}

	private static YearMonth resolvePeriod(Integer year, Integer month) {
		if (year != null && month != null) {
			return YearMonth.of(year, month);
		}
		return YearMonth.now();
	}

	private List<Client> accessibleClients(UUID firmId, String query) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Client> clients;
		if (clientAccessService.isAdmin()) {
			clients = clientRepository.findByFirmIdAndDeletedAtIsNullOrderByNameAsc(firmId);
		} else {
			Set<UUID> ids = clientAccessService.accessibleClientIds();
			if (ids.isEmpty()) {
				return List.of();
			}
			clients = clientRepository.findByFirmIdAndDeletedAtIsNullAndIdInOrderByNameAsc(firmId, ids);
		}
		if (needle.isBlank()) {
			return clients;
		}
		return clients.stream()
				.filter(client -> client.getName() != null && client.getName().toLowerCase(Locale.ROOT).contains(needle))
				.toList();
	}

	private static void assertPracticeRole(SecurityUser user) {
		Role.RoleCode role = user.getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new AccessDeniedException("Month-end command center is not available for this role");
		}
	}
}
