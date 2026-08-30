package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.User;
import com.finance.platform.core.audit.AuditAction;
import com.finance.platform.core.audit.AuditEvent;
import com.finance.platform.core.audit.AuditLogger;
import com.finance.platform.core.audit.AuditResourceType;
import com.finance.platform.core.exception.BusinessException;
import com.finance.platform.core.exception.ErrorCodes;
import com.finance.platform.core.exception.ResourceNotFoundException;
import com.finance.platform.core.exception.ValidationException;
import com.finance.platform.finance.application.dto.PeriodResponse;
import com.finance.platform.finance.domain.model.AccountingPeriod;
import com.finance.platform.finance.domain.model.BankTransaction;
import com.finance.platform.finance.domain.model.Client;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.domain.model.Receipt;
import com.finance.platform.finance.domain.model.TransactionStatus;
import com.finance.platform.finance.infrastructure.persistence.AccountingPeriodJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.BankTransactionJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ExpenseJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.IncomeJpaRepository;
import com.finance.platform.finance.infrastructure.persistence.ReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PeriodCloseService {

	private final AccountingPeriodJpaRepository periodRepository;
	private final ClientAccessService clientAccessService;
	private final ReceiptJpaRepository receiptRepository;
	private final ExpenseJpaRepository expenseRepository;
	private final IncomeJpaRepository incomeRepository;
	private final BankTransactionJpaRepository bankTransactionRepository;
	private final DocumentRequestJpaRepository documentRequestRepository;
	private final AuditLogger auditLogger;

	@Transactional(readOnly = true)
	public List<PeriodResponse> list(UUID clientId) {
		clientAccessService.requireReadAccess(clientId);
		return periodRepository.findByClient_IdOrderByPeriodYearDescPeriodMonthDesc(clientId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public PeriodResponse getOrCreate(UUID clientId, int year, int month) {
		validateMonth(year, month);
		Client client = clientAccessService.requireWriteAccess(clientId);
		AccountingPeriod period = periodRepository.findByClient_IdAndPeriodYearAndPeriodMonth(clientId, year, month)
				.orElseGet(() -> {
					AccountingPeriod created = AccountingPeriod.builder()
							.client(client)
							.periodYear(year)
							.periodMonth(month)
							.status(AccountingPeriod.PeriodStatus.OPEN)
							.build();
					created.setFirmId(client.getFirmId());
					return periodRepository.save(created);
				});
		return toResponse(period);
	}

	@Transactional
	public PeriodResponse close(UUID clientId, UUID periodId) {
		clientAccessService.requireApproveAccess(clientId);
		AccountingPeriod period = requirePeriod(clientId, periodId);
		Readiness readiness = calculateReadiness(clientId);
		if (!readiness.blockers().isEmpty()) {
			throw new BusinessException(ErrorCodes.PERIOD_NOT_READY, "Period is not ready to close: " + String.join("; ", readiness.blockers()));
		}
		period.setStatus(AccountingPeriod.PeriodStatus.CLOSED);
		period.setClosedBy(clientAccessService.requireCurrentUserEntity());
		period.setClosedAt(Instant.now());
		AccountingPeriod saved = periodRepository.save(period);
		auditLogger.record(AuditEvent.fromTenant()
				.firmId(saved.getFirmId())
				.action(AuditAction.PERIOD_CLOSED)
				.resourceType(AuditResourceType.PERIOD)
				.resourceId(saved.getId())
				.clientId(clientId)
				.afterState(java.util.Map.of("year", saved.getPeriodYear(), "month", saved.getPeriodMonth()))
				.build());
		return toResponse(saved);
	}

	@Transactional
	public PeriodResponse reopen(UUID clientId, UUID periodId, String reason) {
		clientAccessService.requireApproveAccess(clientId);
		if (reason == null || reason.isBlank()) {
			throw new ValidationException("reason", "Reopen reason is required");
		}
		AccountingPeriod period = requirePeriod(clientId, periodId);
		if (!period.isClosed()) {
			throw new BusinessException(ErrorCodes.INVALID_STATUS_TRANSITION, "Only closed periods can be reopened");
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
				.afterState(java.util.Map.of("reason", reason.trim()))
				.build());
		return toResponse(saved);
	}

	public void assertPeriodOpen(UUID clientId, LocalDate date) {
		if (date == null) {
			return;
		}
		periodRepository.findByClient_IdAndPeriodYearAndPeriodMonth(clientId, date.getYear(), date.getMonthValue())
				.filter(AccountingPeriod::isClosed)
				.ifPresent(period -> {
					throw new BusinessException(ErrorCodes.PERIOD_CLOSED, "Accounting period is closed");
				});
	}

	private PeriodResponse toResponse(AccountingPeriod period) {
		Readiness readiness = calculateReadiness(period.getClient().getId());
		AccountingPeriod.PeriodStatus status = period.getStatus();
		if (!period.isClosed() && readiness.blockers().isEmpty()) {
			status = AccountingPeriod.PeriodStatus.READY_TO_CLOSE;
		} else if (!period.isClosed() && !readiness.blockers().isEmpty()
				&& status == AccountingPeriod.PeriodStatus.OPEN) {
			status = AccountingPeriod.PeriodStatus.IN_REVIEW;
		}
		return new PeriodResponse(
				period.getId(),
				period.getClient().getId(),
				period.getPeriodYear(),
				period.getPeriodMonth(),
				period.isClosed() ? AccountingPeriod.PeriodStatus.CLOSED : status,
				readiness.percent(),
				readiness.blockers(),
				period.getClosedAt(),
				period.getReopenReason()
		);
	}

	private Readiness calculateReadiness(UUID clientId) {
		List<String> blockers = new ArrayList<>();
		long unreviewed = receiptRepository.countByClientIdAndStatusInAndDeletedAtIsNull(
				clientId,
				List.of(Receipt.ReceiptStatus.UPLOADED, Receipt.ReceiptStatus.NEEDS_REVIEW, Receipt.ReceiptStatus.EXTRACTED, Receipt.ReceiptStatus.PROCESSING)
		);
		long drafts = expenseRepository.countByClientIdAndStatus(clientId, TransactionStatus.DRAFT)
				+ incomeRepository.countByClientIdAndStatus(clientId, TransactionStatus.DRAFT);
		long unmatched = bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.UNMATCHED)
				+ bankTransactionRepository.countByClientIdAndMatchStatus(clientId, BankTransaction.MatchStatus.SUGGESTED);
		long missing = documentRequestRepository.countByClient_IdAndStatus(clientId, DocumentRequest.RequestStatus.OPEN);
		if (unreviewed > 0) {
			blockers.add(unreviewed + " documents need review");
		}
		if (drafts > 0) {
			blockers.add(drafts + " draft transactions are unapproved");
		}
		if (unmatched > 0) {
			blockers.add(unmatched + " bank entries are unreconciled");
		}
		if (missing > 0) {
			blockers.add(missing + " requested documents are missing");
		}
		int percent = blockers.isEmpty() ? 100 : Math.max(5, 100 - (int) Math.min(90, (unreviewed + drafts + unmatched + missing) * 6));
		return new Readiness(percent, blockers);
	}

	private AccountingPeriod requirePeriod(UUID clientId, UUID periodId) {
		return periodRepository.findByIdAndClient_Id(periodId, clientId)
				.orElseThrow(() -> new ResourceNotFoundException("AccountingPeriod", periodId));
	}

	private static void validateMonth(int year, int month) {
		if (year < 2000 || month < 1 || month > 12) {
			throw new ValidationException("period", "Invalid accounting period");
		}
	}

	private record Readiness(int percent, List<String> blockers) {
	}
}
