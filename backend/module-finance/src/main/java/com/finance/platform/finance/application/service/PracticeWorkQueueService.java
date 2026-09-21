package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import com.finance.platform.auth.infrastructure.persistence.UserJpaRepository;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.ClientPortfolioItemResponse;
import com.finance.platform.finance.application.dto.CloseWorkQueueItemResponse;
import com.finance.platform.finance.application.dto.StaffWorkloadItemResponse;
import com.finance.platform.finance.application.dto.WorkItemResponse;
import com.finance.platform.finance.application.dto.WorkSummaryResponse;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.PracticeWorkQueryRepository;
import com.finance.platform.finance.infrastructure.persistence.PracticeWorkQueryRepository.WorkRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeWorkQueueService {

	private final PracticeWorkQueryRepository workQueries;
	private final ClientAccessService clientAccessService;
	private final ClientJpaRepository clientRepository;
	private final UserJpaRepository userRepository;
	private final PeriodCloseService periodCloseService;
	private final BankTransactionJpaRepository bankTransactionRepository;
	private final CloseReadinessService closeReadinessService;
	private final MonthEndCommandCenterService monthEndCommandCenterService;

	@Transactional(readOnly = true)
	public WorkSummaryResponse summary() {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		assertPracticeRole(user);
		boolean admin = clientAccessService.isAdmin();
		Set<UUID> clientIds = admin ? Set.of() : clientAccessService.accessibleClientIds();
		if (!admin && clientIds.isEmpty()) {
			return emptySummary();
		}
		UUID firmId = user.getFirmId();
		long ready = countReadyToClose(firmId, clientIds, admin);
		return new WorkSummaryResponse(
				workQueries.countDocumentsNeedingReview(firmId, clientIds, admin),
				workQueries.countProcessingFailures(firmId, clientIds, admin),
				workQueries.countPendingApprovals(firmId, clientIds, admin),
				workQueries.countOpenDocumentRequests(firmId, clientIds, admin),
				workQueries.countOverdueDocumentRequests(firmId, clientIds, admin),
				workQueries.countBankUnresolved(firmId, clientIds, admin),
				ready
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<WorkItemResponse> myWork(
			String type,
			String priority,
			UUID clientId,
			Boolean overdueOnly,
			int page,
			int size
	) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		assertPracticeRole(user);
		boolean admin = clientAccessService.isAdmin();
		Set<UUID> clientIds = admin ? Set.of() : clientAccessService.accessibleClientIds();
		if (!admin && clientIds.isEmpty()) {
			return new PageResponse<>(List.of(), page, size, 0);
		}
		if (clientId != null && !admin && !clientIds.contains(clientId)) {
			throw new AccessDeniedException("Access denied to client");
		}
		UUID firmId = user.getFirmId();
		boolean scopedAll = admin && clientId == null;
		Set<UUID> scope = clientId != null ? Set.of(clientId) : clientIds;
		int fetchLimit = Math.min(Math.max(size, 1), 100) * 3;
		List<WorkRow> rows = new ArrayList<>();
		if (type == null || type.isBlank() || "DOCUMENT_REVIEW".equalsIgnoreCase(type)) {
			rows.addAll(workQueries.listDocumentReview(firmId, scope, scopedAll || clientId != null, fetchLimit));
		}
		if (type == null || type.isBlank() || "DOCUMENT_PROCESSING_FAILURE".equalsIgnoreCase(type)) {
			rows.addAll(workQueries.listProcessingFailures(firmId, scope, scopedAll || clientId != null, fetchLimit));
		}
		if (type == null || type.isBlank() || "TRANSACTION_APPROVAL".equalsIgnoreCase(type)) {
			rows.addAll(workQueries.listDraftApprovals(firmId, scope, scopedAll || clientId != null, fetchLimit));
		}
		if (type == null || type.isBlank() || "DOCUMENT_REQUEST".equalsIgnoreCase(type)) {
			rows.addAll(workQueries.listDocumentRequests(firmId, scope, scopedAll || clientId != null, fetchLimit));
		}
		if (type == null || type.isBlank() || "BANK_RECONCILIATION".equalsIgnoreCase(type)) {
			rows.addAll(workQueries.listBankReconciliation(firmId, scope, scopedAll || clientId != null, fetchLimit));
		}
		if (type == null || type.isBlank() || "PERIOD_CLOSE".equalsIgnoreCase(type)) {
			rows.addAll(readyCloseRows(firmId, scope, scopedAll || clientId != null));
		}
		List<WorkItemResponse> items = rows.stream()
				.map(PracticeWorkQueueService::toResponse)
				.filter(item -> priority == null || priority.isBlank() || item.priority().equalsIgnoreCase(priority))
				.filter(item -> overdueOnly == null || !overdueOnly || item.overdue())
				.sorted(Comparator
						.comparingInt((WorkItemResponse item) -> priorityRank(item.priority()))
						.thenComparing(WorkItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
		int capped = Math.min(Math.max(size, 1), 100);
		int from = Math.min(page * capped, items.size());
		int to = Math.min(from + capped, items.size());
		return new PageResponse<>(items.subList(from, to), page, capped, items.size());
	}

	@Transactional(readOnly = true)
	public List<ClientPortfolioItemResponse> portfolio(String query) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		assertPracticeRole(user);
		UUID firmId = user.getFirmId();
		List<Client> clients = accessibleClients(firmId, query);
		YearMonth current = YearMonth.now();
		LocalDate from = current.atDay(1);
		LocalDate to = current.atEndOfMonth();
		return clients.stream().map(client -> {
			long review = workQueries.countDocumentsNeedingReview(firmId, Set.of(client.getId()), false);
			long bankUnresolved = workQueries.countBankUnresolved(firmId, Set.of(client.getId()), false);
			long bankTotal = bankTransactionRepository.countImportedInPeriod(client.getId(), from, to);
			long bankMatched = bankTotal == 0 ? 0 : bankTotal - bankUnresolved;
			Integer reconPercent = bankTotal == 0 ? null : (int) Math.round(bankMatched * 100.0 / bankTotal);
			var readiness = closeReadinessService.evaluate(firmId, client.getId(), null, from, to);
			String accountantName = null;
			if (client.getPrimaryAccountantUserId() != null) {
				accountantName = userRepository.findById(client.getPrimaryAccountantUserId())
						.map(User::getFullName).orElse(null);
			}
			String closeStatus = readiness.ready() ? "Ready" : "Blocked";
			return new ClientPortfolioItemResponse(
					client.getId(),
					client.getName(),
					client.getPrimaryAccountantUserId(),
					accountantName,
					review,
					bankUnresolved,
					reconPercent,
					closeStatus,
					readiness.ready(),
					readiness.readinessPercent()
			);
		}).toList();
	}

	@Transactional(readOnly = true)
	public List<StaffWorkloadItemResponse> staffWorkload() {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		if (user.getRole() != Role.RoleCode.ADMIN) {
			throw new AccessDeniedException("Only administrators can view staff workload");
		}
		UUID firmId = user.getFirmId();
		return userRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
				.filter(u -> u.getRole().getCode() == Role.RoleCode.ACCOUNTANT || u.getRole().getCode() == Role.RoleCode.ADMIN)
				.map(accountant -> {
					Set<UUID> assignedClients = clientRepository.findByFirmIdAndDeletedAtIsNull(firmId).stream()
							.filter(client -> accountant.getId().equals(client.getPrimaryAccountantUserId()))
							.map(Client::getId)
							.collect(Collectors.toSet());
					if (assignedClients.isEmpty()) {
						return new StaffWorkloadItemResponse(
								accountant.getId(), accountant.getFullName(), 0, 0, 0, 0, 0, 0, 0, 0);
					}
					var portfolio = monthEndCommandCenterService.commandCenter(
							null, null, null, null, null, accountant.getId(), null);
					int ready = portfolio.summary().ready();
					int blocked = portfolio.summary().blocked();
					int attention = portfolio.summary().needsAttention();
					return new StaffWorkloadItemResponse(
							accountant.getId(),
							accountant.getFullName(),
							workQueries.countDocumentsNeedingReview(firmId, assignedClients, false),
							workQueries.countPendingApprovals(firmId, assignedClients, false),
							workQueries.countBankUnresolved(firmId, assignedClients, false),
							countReadyToClose(firmId, assignedClients, false),
							assignedClients.size(),
							ready,
							blocked,
							attention
					);
				})
				.toList();
	}

	private List<WorkRow> readyCloseRows(UUID firmId, Set<UUID> clientIds, boolean allClients) {
		YearMonth current = YearMonth.now();
		PageResponse<CloseWorkQueueItemResponse> queue = periodCloseService.workQueue(
				current.getYear(), current.getMonthValue(), "READY", true, null, 0, 200);
		return queue.content().stream()
				.filter(item -> allClients || clientIds.contains(item.clientId()))
				.map(item -> new WorkRow(
						"PERIOD_CLOSE",
						"NORMAL",
						item.clientId(),
						item.clientName(),
						"Ready to close",
						item.year() + "-" + String.format("%02d", item.month()),
						item.periodId(),
						"/app/close/" + item.clientId() + "/" + item.periodId(),
						null,
						false,
						null,
						null,
						null
				))
				.toList();
	}

	private long countReadyToClose(UUID firmId, Set<UUID> clientIds, boolean allClients) {
		YearMonth current = YearMonth.now();
		PageResponse<CloseWorkQueueItemResponse> queue = periodCloseService.workQueue(
				current.getYear(), current.getMonthValue(), "READY", true, null, 0, 500);
		if (allClients) {
			return queue.totalElements();
		}
		return queue.content().stream().filter(item -> clientIds.contains(item.clientId())).count();
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

	private static WorkItemResponse toResponse(WorkRow row) {
		return new WorkItemResponse(
				row.type(),
				row.priority(),
				row.clientId(),
				row.clientName(),
				row.title(),
				row.description(),
				row.resourceId(),
				row.actionUrl(),
				row.dueDate(),
				row.overdue(),
				row.createdAt(),
				row.assignedUserId(),
				row.assignedUserName()
		);
	}

	private static int priorityRank(String priority) {
		if ("URGENT".equalsIgnoreCase(priority) || "HIGH".equalsIgnoreCase(priority)) {
			return 0;
		}
		if ("NORMAL".equalsIgnoreCase(priority) || "MEDIUM".equalsIgnoreCase(priority)) {
			return 1;
		}
		return 2;
	}

	private static void assertPracticeRole(SecurityUser user) {
		Role.RoleCode role = user.getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new AccessDeniedException("Practice work queue is not available for this role");
		}
	}

	private static WorkSummaryResponse emptySummary() {
		return new WorkSummaryResponse(0, 0, 0, 0, 0, 0, 0);
	}
}
