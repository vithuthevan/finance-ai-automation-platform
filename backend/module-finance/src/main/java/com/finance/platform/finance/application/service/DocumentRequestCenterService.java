package com.finance.platform.finance.application.service;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import com.finance.platform.finance.application.dto.DocumentRequestCenterItemResponse;
import com.finance.platform.finance.domain.model.DocumentRequest;
import com.finance.platform.finance.infrastructure.persistence.DocumentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentRequestCenterService {

	private final DocumentRequestJpaRepository requestRepository;
	private final ClientAccessService clientAccessService;

	@Transactional(readOnly = true)
	public PageResponse<DocumentRequestCenterItemResponse> list(
			DocumentRequest.RequestStatus status,
			Boolean overdueOnly,
			int page,
			int size
	) {
		SecurityUser user = SecurityUtils.requireCurrentUser();
		assertPracticeRole(user);
		boolean admin = clientAccessService.isAdmin();
		Set<UUID> accessible = admin ? Set.of() : clientAccessService.accessibleClientIds();
		if (!admin && accessible.isEmpty()) {
			return new PageResponse<>(java.util.List.of(), page, size, 0);
		}

		Page<DocumentRequest> results = requestRepository.searchByFirm(
				user.getFirmId(),
				status,
				PageRequests.of(page, size, Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))));

		LocalDate today = LocalDate.now();
		var mapped = results.getContent().stream()
				.filter(request -> admin || accessible.contains(request.getClient().getId()))
				.map(request -> toItem(request, today))
				.filter(item -> overdueOnly == null || !overdueOnly || item.overdue())
				.toList();

		return new PageResponse<>(mapped, results.getNumber(), results.getSize(), results.getTotalElements());
	}

	private static DocumentRequestCenterItemResponse toItem(DocumentRequest request, LocalDate today) {
		boolean overdue = request.getDueDate() != null
				&& request.getDueDate().isBefore(today)
				&& request.getStatus() != DocumentRequest.RequestStatus.COMPLETED
				&& request.getStatus() != DocumentRequest.RequestStatus.CANCELLED;
		long ageDays = request.getCreatedAt() == null ? 0
				: ChronoUnit.DAYS.between(request.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), today);
		String nextAction = switch (request.getStatus()) {
			case OPEN -> overdue ? "Send reminder or follow up" : "Waiting on client upload";
			case UPLOADED -> "Review upload and complete";
			case COMPLETED -> "Completed";
			case CANCELLED -> "Cancelled";
		};
		String actionPath = "/app/requests?clientId=" + request.getClient().getId();
		Integer year = request.getPeriod() == null ? null : request.getPeriod().getPeriodYear();
		Integer month = request.getPeriod() == null ? null : request.getPeriod().getPeriodMonth();
		return new DocumentRequestCenterItemResponse(
				request.getId(),
				request.getClient().getId(),
				request.getClient().getName(),
				request.getTitle() != null ? request.getTitle() : request.getDescription(),
				request.getDescription(),
				request.getDocumentType(),
				request.getDueDate(),
				request.getStatus(),
				overdue,
				ageDays,
				request.getCreatedAt(),
				request.getLastReminderAt(),
				request.getReminderCount(),
				request.getPeriod() == null ? null : request.getPeriod().getId(),
				year,
				month,
				nextAction,
				actionPath
		);
	}

	private static void assertPracticeRole(SecurityUser user) {
		Role.RoleCode role = user.getRole();
		if (role != Role.RoleCode.ADMIN && role != Role.RoleCode.ACCOUNTANT) {
			throw new AccessDeniedException("Document request center is not available for this role");
		}
	}
}
