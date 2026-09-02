package com.finance.platform.controller;

import com.finance.platform.auth.api.UserFacade;
import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.infrastructure.security.SecurityUser;
import com.finance.platform.auth.infrastructure.security.SecurityUtils;
import com.finance.platform.core.audit.AuditLog;
import com.finance.platform.core.audit.AuditLogJpaRepository;
import com.finance.platform.core.dto.PageRequests;
import com.finance.platform.core.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityFeedController {

	private final AuditLogJpaRepository auditLogRepository;
	private final UserFacade userFacade;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'AUDITOR')")
	public PageResponse<ActivityView> recent(
			@RequestParam(required = false) UUID clientId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		UUID firmId = currentUser.getFirmId();
		Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("firmId"), firmId);
		if (clientId != null) {
			assertClientAccess(currentUser, clientId);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
		} else if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			Set<UUID> assigned = userFacade.getAccessibleClientIds(currentUser.getId());
			spec = spec.and((root, query, cb) -> assigned.isEmpty()
					? cb.isNull(root.get("clientId"))
					: cb.or(cb.isNull(root.get("clientId")), root.get("clientId").in(assigned)));
		}
		Page<AuditLog> results = auditLogRepository.findAll(
				spec, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
		return new PageResponse<>(
				results.map(ActivityView::from).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements());
	}

	private void assertClientAccess(SecurityUser user, UUID clientId) {
		if (user.getRole() != Role.RoleCode.ADMIN && !userFacade.hasAccessToClient(user.getId(), clientId)) {
			throw new org.springframework.security.access.AccessDeniedException("Access denied to client activity");
		}
	}

	public record ActivityView(
			UUID id,
			Instant occurredAt,
			String action,
			UUID clientId,
			String resourceType,
			UUID resourceId,
			String summary
	) {
		static ActivityView from(AuditLog log) {
			return new ActivityView(
					log.getId(),
					log.getOccurredAt(),
					log.getAction(),
					log.getClientId(),
					log.getResourceType(),
					log.getResourceId(),
					log.getAction().replace('_', ' ').toLowerCase());
		}
	}
}
