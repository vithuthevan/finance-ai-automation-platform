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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

	private final AuditLogJpaRepository auditLogRepository;
	private final UserFacade userFacade;

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
	public PageResponse<AuditView> list(
			@RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) UUID clientId,
			@RequestParam(required = false) UUID resourceId,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		SecurityUser currentUser = SecurityUtils.requireCurrentUser();
		UUID firmId = currentUser.getFirmId();
		Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("firmId"), firmId);
		if (actorUserId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
		}
		if (clientId != null) {
			if (currentUser.getRole() != Role.RoleCode.ADMIN
					&& !userFacade.hasAccessToClient(currentUser.getId(), clientId)) {
				throw new org.springframework.security.access.AccessDeniedException("Access denied to client audit");
			}
			spec = spec.and((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
		} else if (currentUser.getRole() != Role.RoleCode.ADMIN) {
			var assigned = userFacade.getAccessibleClientIds(currentUser.getId());
			spec = spec.and((root, query, cb) -> assigned.isEmpty()
					? cb.isNull(root.get("clientId"))
					: cb.or(cb.isNull(root.get("clientId")), root.get("clientId").in(assigned)));
		}
		if (resourceId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceId"), resourceId));
		}
		if (resourceType != null && !resourceType.isBlank()) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceType"), resourceType));
		}
		if (action != null && !action.isBlank()) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
		}
		if (from != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
		}
		if (to != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
		}
		Page<AuditLog> results = auditLogRepository.findAll(
				spec, PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
		return new PageResponse<>(
				results.map(AuditView::from).getContent(),
				results.getNumber(),
				results.getSize(),
				results.getTotalElements()
		);
	}

	public record AuditView(
			UUID id,
			Instant occurredAt,
			UUID actorUserId,
			String actorRole,
			String action,
			String resourceType,
			UUID resourceId,
			UUID clientId,
			Map<String, Object> beforeState,
			Map<String, Object> afterState,
			String outcome
	) {
		static AuditView from(AuditLog log) {
			return new AuditView(
					log.getId(),
					log.getOccurredAt(),
					log.getActorUserId(),
					log.getActorRole(),
					log.getAction(),
					log.getResourceType(),
					log.getResourceId(),
					log.getClientId(),
					log.getBeforeState(),
					log.getAfterState(),
					log.getOutcome() != null ? log.getOutcome().name() : null
			);
		}
	}
}
