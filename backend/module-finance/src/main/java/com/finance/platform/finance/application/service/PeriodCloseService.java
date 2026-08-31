package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.CloseWorkQueueItemResponse;
import com.finance.platform.finance.application.dto.PeriodReadinessResponse;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PeriodCloseService {

	private final AccountingPeriodJpaRepository periodRepository;
	private final ClientJpaRepository clientRepository;
	private final ClientAccessService clientAccessService;
	private final CloseReadinessService closeReadinessService;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<PeriodResponse> list(UUID clientId) {
		Client client = clientAccessService.requireReadAccess(clientId);
		return periodRepository.findDetailedByClient_Id(clientId).stream()
				.map(period -> toResponse(period, client, evaluate(period)))
				.toList();
	}

	@Transactional(readOnly = true)
	public PeriodResponse get(UUID clientId, UUID periodId) {
		Client client = clientAccessService.requireReadAccess(clientId);
		AccountingPeriod period = requirePeriod(clientId, periodId);
		return toResponse(period, client, evaluate(period));
	}

	@Transactional(readOnly = true)
	public PeriodReadinessResponse readiness(UUID clientId, UUID periodId) {
		clientAccessService.requireReadAccess(clientId);
		return evaluate(requirePeriod(clientId, periodId));
	}

	@Transactional
	public PeriodResponse getOrCreate(UUID clientId, int year, int month) {
		validateMonth(year, month);
		Client client = clientAccessService.requireWriteAccess(clientId);
		return periodRepository.findByClient_IdAndPeriodYearAndPeriodMonth(clientId, year, month)
				.map(period -> toResponse(period, client, evaluate(period)))
				.orElseGet(() -> createMonth(client, year, month));
	}

	@Transactional
	public PeriodResponse startReview(UUID clientId, UUID periodId) {
		Client client = clientAccessService.requireApproveAccess(clientId);
		AccountingPeriod period = requirePeriod(clientId, periodId);
		if (period.isClosed()) {
			throw new BusinessException(ErrorCodes.PERIOD_ALREADY_CLOSED, "A closed period cannot be moved to review");
		}
		if (period.getStatus() != AccountingPeriod.PeriodStatus.IN_REVIEW) {
			User user = clientAccessService.requireCurrentUserEntity();
			period.setStatus(AccountingPeriod.PeriodStatus.IN_REVIEW);
			period.setReviewStartedAt(Instant.now());
			period.setReviewStartedBy(user);
			periodRepository.save(period);
			auditLogger.record(AuditEvent.fromTenant()
					.firmId(period.getFirmId())
					.action(AuditAction.PERIOD_REVIEW_STARTED)
					.resourceType(AuditResourceType.PERIOD)
					.resourceId(period.getId())
					.clientId(clientId)
					.afterState(Map.of(
							"year", period.getPeriodYear(),
							"month", period.getPeriodMonth()
					))
					.build());
		}
		return toResponse(period, client, evaluate(period));
	}

	@Transactional
	public PeriodResponse close(UUID clientId, UUID periodId, String closeNote) {
		Client client = clientAccessService.requireApproveAccess(clientId);
		AccountingPeriod period = requirePeriod(clientId, periodId);
		if (period.isClosed()) {
			throw new BusinessException(ErrorCodes.PERIOD_ALREADY_CLOSED, "Period is already closed");
		}
		PeriodReadinessResponse readiness = evaluate(period);
		if (!readiness.ready()) {
			Map<String, Object> extras = new LinkedHashMap<>();
			extras.put("blockers", readiness.blockers());
			extras.put("ready", false);
			extras.put("readinessPercent", readiness.readinessPercent());
			throw new BusinessException(
					ErrorCodes.PERIOD_NOT_READY_TO_CLOSE,
					"Period is not ready to close",
					extras);
		}
		User user = clientAccessService.requireCurrentUserEntity();
		period.setStatus(AccountingPeriod.PeriodStatus.CLOSED);
		period.setClosedBy(user);
		period.setClosedAt(Instant.now());
		period.setCloseNote(closeNote == null || closeNote.isBlank() ? null : closeNote.trim());
		AccountingPeriod saved = periodRepository.save(period);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.PERIOD_CLOSED)
				.resourceType(AuditResourceType.PERIOD)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of(
						"year", saved.getPeriodYear(),
						"month", saved.getPeriodMonth(),
						"closeNote", saved.getCloseNote() == null ? "" : saved.getCloseNote()
				))
				.build());
		return toResponse(saved, client, evaluate(saved));
	}

	@Transactional
	public PeriodResponse reopen(UUID clientId, UUID periodId, String reason) {
		if (!clientAccessService.isAdmin()) {
			throw new AccessDeniedException("Only administrators can reopen a closed period");
		}
		Client client = clientAccessService.requireReadAccess(clientId);
		if (reason == null || reason.isBlank()) {
			throw new BusinessException(ErrorCodes.PERIOD_REOPEN_REASON_REQUIRED, "A reopen reason is required");
		}
		AccountingPeriod period = requirePeriod(clientId, periodId);
		if (!period.isClosed()) {
			throw new BusinessException(ErrorCodes.PERIOD_NOT_CLOSED, "Only closed periods can be reopened");
		}
		User user = clientAccessService.requireCurrentUserEntity();
		period.setStatus(AccountingPeriod.PeriodStatus.REOPENED);
		period.setReopenReason(reason.trim());
		period.setReopenedBy(user);
		period.setReopenedAt(Instant.now());
		AccountingPeriod saved = periodRepository.save(period);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.PERIOD_REOPENED)
				.resourceType(AuditResourceType.PERIOD)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(Map.of("reason", reason.trim()))
				.build());
		return toResponse(saved, client, evaluate(saved));
	}

	@Transactional(readOnly = true)
	public PageResponse<CloseWorkQueueItemResponse> workQueue(
			int year,
			int month,
			String status,
			Boolean ready,
			String query,
			int page,
			int size
	) {
		validateMonth(year, month);
		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDate from = yearMonth.atDay(1);
		LocalDate to = yearMonth.atEndOfMonth();
		UUID firmId = clientAccessService.requireCurrentUserEntity().getFirmId();
		List<Client> clients = accessibleClients(firmId, query);
		List<CloseWorkQueueItemResponse> items = new ArrayList<>();
		for (Client client : clients) {
			AccountingPeriod period = periodRepository
					.findByClient_IdAndPeriodYearAndPeriodMonth(client.getId(), year, month)
					.orElse(null);
			PeriodReadinessResponse readiness = closeReadinessService.evaluate(
					firmId,
					client.getId(),
					period == null ? null : period.getId(),
					period == null ? from : period.getStartDate(),
					period == null ? to : period.getEndDate()
			);
			AccountingPeriod.PeriodStatus persisted = period == null
					? AccountingPeriod.PeriodStatus.OPEN
					: period.getStatus();
			CloseWorkQueueItemResponse item = new CloseWorkQueueItemResponse(
					client.getId(),
					client.getName(),
					period == null ? null : period.getId(),
					year,
					month,
					period == null ? from : period.getStartDate(),
					period == null ? to : period.getEndDate(),
					persisted,
					displayStatus(persisted, readiness.ready()),
					readiness.ready() && persisted != AccountingPeriod.PeriodStatus.CLOSED,
					readiness.readinessPercent(),
					readiness.blockers().stream().mapToInt(finding -> finding.count()).sum()
			);
			if (matchesFilters(item, status, ready)) {
				items.add(item);
			}
		}
		items.sort(Comparator.comparing(CloseWorkQueueItemResponse::clientName, String.CASE_INSENSITIVE_ORDER));
		Pageable pageable = PageRequests.of(page, size, org.springframework.data.domain.Sort.unsorted());
		int fromIndex = Math.min(pageable.getPageNumber() * pageable.getPageSize(), items.size());
		int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
		return new PageResponse<>(items.subList(fromIndex, toIndex), pageable.getPageNumber(), pageable.getPageSize(), items.size());
	}

	public void assertPeriodOpen(UUID clientId, LocalDate date) {
		if (date == null) {
			return;
		}
		UUID firmId = clientAccessService.requireCurrentUserEntity().getFirmId();
		periodRepository.findClosedContaining(firmId, clientId, AccountingPeriod.PeriodStatus.CLOSED, date)
				.ifPresent(period -> {
					throw new BusinessException(ErrorCodes.PERIOD_CLOSED,
							"This date belongs to a closed bookkeeping period. Reopen the period to change financial data.");
				});
	}

	public void assertPeriodOpen(UUID clientId, LocalDate... dates) {
		if (dates == null) {
			return;
		}
		for (LocalDate date : dates) {
			assertPeriodOpen(clientId, date);
		}
	}

	public boolean isPeriodClosed(UUID clientId, LocalDate date) {
		if (date == null) {
			return false;
		}
		UUID firmId = clientAccessService.requireCurrentUserEntity().getFirmId();
		return periodRepository.findClosedContaining(firmId, clientId, AccountingPeriod.PeriodStatus.CLOSED, date).isPresent();
	}

	private PeriodResponse createMonth(Client client, int year, int month) {
		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDate start = yearMonth.atDay(1);
		LocalDate end = yearMonth.atEndOfMonth();
		if (periodRepository.existsOverlapping(client.getFirmId(), client.getId(), start, end)) {
			throw new BusinessException(ErrorCodes.ACCOUNTING_PERIOD_OVERLAP,
					"An accounting period already exists that overlaps " + start + " to " + end);
		}
		AccountingPeriod created = AccountingPeriod.builder()
				.client(client)
				.periodYear(year)
				.periodMonth(month)
				.startDate(start)
				.endDate(end)
				.status(AccountingPeriod.PeriodStatus.OPEN)
				.build();
		created.setFirmId(client.getFirmId());
		AccountingPeriod saved = periodRepository.save(created);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.PERIOD_CREATED)
				.resourceType(AuditResourceType.PERIOD)
				.resourceId(saved.getId())
				.clientId(client.getId())
				.afterState(Map.of("year", year, "month", month, "startDate", start.toString(), "endDate", end.toString()))
				.build());
		return toResponse(saved, client, evaluate(saved));
	}

	private PeriodReadinessResponse evaluate(AccountingPeriod period) {
		return closeReadinessService.evaluate(
				period.getFirmId(),
				period.getClient().getId(),
				period.getId(),
				period.getStartDate(),
				period.getEndDate()
		);
	}

	private PeriodResponse toResponse(AccountingPeriod period, Client client, PeriodReadinessResponse readiness) {
		AccountingPeriod.PeriodStatus status = period.getStatus();
		if (status == AccountingPeriod.PeriodStatus.READY_TO_CLOSE) {
			status = AccountingPeriod.PeriodStatus.OPEN;
		}
		return new PeriodResponse(
				period.getId(),
				client.getId(),
				client.getName(),
				period.getPeriodYear(),
				period.getPeriodMonth(),
				period.getStartDate(),
				period.getEndDate(),
				status,
				readiness.ready() && !period.isClosed(),
				readiness.readinessPercent(),
				readiness.blockers().stream().mapToInt(finding -> finding.count()).sum(),
				readiness.blockers(),
				period.getReviewStartedAt(),
				period.getReviewStartedBy() == null ? null : period.getReviewStartedBy().getId(),
				period.getReviewStartedBy() == null ? null : period.getReviewStartedBy().getFullName(),
				period.getClosedAt(),
				period.getClosedBy() == null ? null : period.getClosedBy().getId(),
				period.getClosedBy() == null ? null : period.getClosedBy().getFullName(),
				period.getCloseNote(),
				period.getReopenedAt(),
				period.getReopenedBy() == null ? null : period.getReopenedBy().getId(),
				period.getReopenedBy() == null ? null : period.getReopenedBy().getFullName(),
				period.getReopenReason(),
				period.getCreatedAt()
		);
	}

	private AccountingPeriod requirePeriod(UUID clientId, UUID periodId) {
		return periodRepository.findDetailedByIdAndClient_Id(periodId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("Accounting period", periodId));
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

	private static boolean matchesFilters(CloseWorkQueueItemResponse item, String status, Boolean ready) {
		if (ready != null && item.ready() != ready) {
			return false;
		}
		if (status == null || status.isBlank()) {
			return true;
		}
		String normalized = status.trim().toUpperCase(Locale.ROOT);
		if ("READY".equals(normalized)) {
			return item.ready();
		}
		return item.displayStatus().equalsIgnoreCase(normalized)
				|| item.status().name().equalsIgnoreCase(normalized);
	}

	private static String displayStatus(AccountingPeriod.PeriodStatus status, boolean ready) {
		if (status == AccountingPeriod.PeriodStatus.CLOSED) {
			return "CLOSED";
		}
		if (status == AccountingPeriod.PeriodStatus.IN_REVIEW) {
			return "IN_REVIEW";
		}
		if (status == AccountingPeriod.PeriodStatus.REOPENED) {
			return ready ? "READY" : "REOPENED";
		}
		if (ready) {
			return "READY";
		}
		return "OPEN";
	}

	private static void validateMonth(int year, int month) {
		if (year < 2000 || month < 1 || month > 12) {
			throw new ValidationException("period", "Invalid accounting period");
		}
	}
}
